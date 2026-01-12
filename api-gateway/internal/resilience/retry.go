package resilience

import (
	"context"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/cenkalti/backoff/v4"
	"api-gateway-go/internal/config"
)

// RetryConfig representa a configuração de retry para um serviço
type RetryConfig struct {
	MaxAttempts int
	MinBackoff  time.Duration
}

// RetryManager gerencia configurações de retry por serviço
type RetryManager struct {
	configs map[ServiceName]RetryConfig
}

// NewRetryManager cria um novo gerenciador de retry
func NewRetryManager(cfg *config.Config) *RetryManager {
	manager := &RetryManager{
		configs: make(map[ServiceName]RetryConfig),
	}

	// Configurar retry para catalog service
	manager.configs[ServiceCatalog] = RetryConfig{
		MaxAttempts: cfg.Services.Catalog.Retry.MaxAttempts,
		MinBackoff:  cfg.GetCatalogRetryBackoff(),
	}

	// Configurar retry para search service
	manager.configs[ServiceSearch] = RetryConfig{
		MaxAttempts: cfg.Services.Search.Retry.MaxAttempts,
		MinBackoff:  cfg.GetSearchRetryBackoff(),
	}

	return manager
}

// GetRetryConfig retorna a configuração de retry para um serviço específico
func (rm *RetryManager) GetRetryConfig(serviceName ServiceName) (RetryConfig, error) {
	retryConfig, exists := rm.configs[serviceName]
	if !exists {
		return RetryConfig{}, fmt.Errorf("configuração de retry não encontrada para o serviço: %s", serviceName)
	}
	return retryConfig, nil
}

// ShouldRetry determina se um erro deve ser retentado
// Retry apenas para erros 5xx (server errors) e timeouts
func ShouldRetry(err error, httpStatus int) bool {
	if err != nil {
		// Verificar se é um erro de timeout
		if isTimeoutError(err) {
			return true
		}
	}

	// Retry apenas para erros 5xx (server errors)
	// Não fazer retry para 4xx (client errors) pois são erros permanentes
	return httpStatus >= 500 && httpStatus < 600
}

// isTimeoutError verifica se o erro é relacionado a timeout
func isTimeoutError(err error) bool {
	if err == nil {
		return false
	}
	errStr := strings.ToLower(err.Error())
	return strings.Contains(errStr, "timeout") ||
		strings.Contains(errStr, "deadline exceeded") ||
		strings.Contains(errStr, "context deadline exceeded")
}

// ExecuteWithRetry executa uma função com retry e backoff exponencial
// A função deve retornar o resultado e um erro
// Se o erro for retentável (5xx ou timeout), será feito retry
func (rm *RetryManager) ExecuteWithRetry(
	ctx context.Context,
	serviceName ServiceName,
	fn func() (interface{}, error),
) (interface{}, error) {
	retryConfig, err := rm.GetRetryConfig(serviceName)
	if err != nil {
		return nil, err
	}

	var lastErr error
	var lastResult interface{}
	attempt := 0

	// Configurar backoff exponencial
	backoffConfig := backoff.NewExponentialBackOff()
	backoffConfig.InitialInterval = retryConfig.MinBackoff
	backoffConfig.MaxInterval = 30 * time.Second // Limite máximo de intervalo
	backoffConfig.Multiplier = 2.0                // Multiplicador exponencial
	backoffConfig.MaxElapsedTime = 0              // Sem limite de tempo total (controlado por MaxAttempts)
	backoffConfig.Reset()

	// Executar com retry
	operation := func() error {
		attempt++

		// Verificar se excedeu o número máximo de tentativas
		if attempt > retryConfig.MaxAttempts {
			if lastErr != nil {
				return backoff.Permanent(fmt.Errorf("excedido número máximo de tentativas (%d): %w", retryConfig.MaxAttempts, lastErr))
			}
			return backoff.Permanent(fmt.Errorf("excedido número máximo de tentativas (%d)", retryConfig.MaxAttempts))
		}

		result, err := fn()

		// Se não houver erro, retornar sucesso
		if err == nil {
			lastResult = result
			return nil
		}

		lastErr = err

		// Verificar se o erro é retentável
		// Para erros HTTP, precisamos extrair o status code
		httpStatus := extractHTTPStatus(err)
		if !ShouldRetry(err, httpStatus) {
			// Erro não retentável, parar retry
			return backoff.Permanent(err)
		}

		// Erro retentável, continuar retry
		return err
	}

	// Executar operação com backoff
	err = backoff.Retry(operation, backoff.WithContext(backoffConfig, ctx))

	// Se o erro for permanente, retorná-lo
	if permanentErr, ok := err.(*backoff.PermanentError); ok {
		return nil, permanentErr.Err
	}

	// Se ainda houver erro após todas as tentativas
	if err != nil {
		// Usar lastErr se disponível, caso contrário usar err
		if lastErr != nil {
			return nil, fmt.Errorf("falha após %d tentativas: %w", attempt, lastErr)
		}
		return nil, fmt.Errorf("falha após %d tentativas: %w", attempt, err)
	}

	return lastResult, nil
}

// ExecuteWithRetryAndHTTPStatus executa uma função que retorna HTTP response e erro
// Útil para operações HTTP onde precisamos verificar o status code
func (rm *RetryManager) ExecuteWithRetryAndHTTPStatus(
	ctx context.Context,
	serviceName ServiceName,
	fn func() (*http.Response, error),
) (*http.Response, error) {
	retryConfig, err := rm.GetRetryConfig(serviceName)
	if err != nil {
		return nil, err
	}

	var lastResponse *http.Response
	var lastErr error
	attempt := 0

	// Configurar backoff exponencial
	backoffConfig := backoff.NewExponentialBackOff()
	backoffConfig.InitialInterval = retryConfig.MinBackoff
	backoffConfig.MaxInterval = 30 * time.Second
	backoffConfig.Multiplier = 2.0
	backoffConfig.MaxElapsedTime = 0
	backoffConfig.Reset()

	// Executar com retry
	operation := func() error {
		attempt++

		// Verificar se excedeu o número máximo de tentativas
		if attempt > retryConfig.MaxAttempts {
			if lastErr != nil {
				return backoff.Permanent(fmt.Errorf("excedido número máximo de tentativas (%d): %w", retryConfig.MaxAttempts, lastErr))
			}
			return backoff.Permanent(fmt.Errorf("excedido número máximo de tentativas (%d)", retryConfig.MaxAttempts))
		}

		response, err := fn()
		lastResponse = response
		lastErr = err

		// Se não houver erro e a resposta for bem-sucedida, retornar sucesso
		if err == nil && response != nil {
			statusCode := response.StatusCode
			if statusCode < 500 {
				// Status 2xx, 3xx, 4xx não precisam de retry
				return nil
			}
			// Status 5xx precisa de retry
			return fmt.Errorf("status HTTP %d", statusCode)
		}

		// Se houver erro, verificar se é retentável
		if err != nil {
			httpStatus := extractHTTPStatus(err)
			if !ShouldRetry(err, httpStatus) {
				return backoff.Permanent(err)
			}
			return err
		}

		// Se a resposta tiver status 5xx, fazer retry
		if response != nil && response.StatusCode >= 500 {
			return fmt.Errorf("status HTTP %d", response.StatusCode)
		}

		return nil
	}

	// Executar operação com backoff
	err = backoff.Retry(operation, backoff.WithContext(backoffConfig, ctx))

	// Se o erro for permanente, retorná-lo diretamente (preserva o tipo original)
	if permanentErr, ok := err.(*backoff.PermanentError); ok {
		return lastResponse, permanentErr.Err
	}

	// Se ainda houver erro após todas as tentativas
	// Mas se lastErr for um erro de serviço (CatalogServiceException ou SearchServiceException),
	// retorná-lo diretamente para preservar o status code
	if err != nil {
		// Verificar se lastErr é um erro de serviço que deve ser preservado
		if lastErr != nil {
			// Usar lastErr diretamente se for um erro de serviço conhecido
			// Isso preserva o status code original (409, etc.)
			return lastResponse, lastErr
		}
		return lastResponse, fmt.Errorf("falha após %d tentativas: %w", attempt, err)
	}

	return lastResponse, nil
}

// extractHTTPStatus tenta extrair o status HTTP de um erro
// Retorna 0 se não conseguir extrair
func extractHTTPStatus(err error) int {
	if err == nil {
		return 0
	}

	// Verificar se o erro contém informações de status HTTP
	// Isso depende de como os erros HTTP são formatados
	errStr := err.Error()

	// Tentar extrair status code comum de mensagens de erro
	// Exemplo: "status code: 500" ou "HTTP 500"
	// Esta é uma implementação básica, pode ser melhorada conforme necessário
	if strings.Contains(errStr, "500") {
		return 500
	}
	if strings.Contains(errStr, "502") {
		return 502
	}
	if strings.Contains(errStr, "503") {
		return 503
	}
	if strings.Contains(errStr, "504") {
		return 504
	}

	return 0
}

