package clients

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"time"

	"github.com/sony/gobreaker"

	"api-gateway-go/internal/config"
	"api-gateway-go/internal/metrics"
	"api-gateway-go/internal/models"
	"api-gateway-go/internal/resilience"
)

// CatalogServiceException representa um erro de comunicação com o catalog-service
type CatalogServiceException struct {
	Message string
	Status  int
	Err     error
}

func (e *CatalogServiceException) Error() string {
	if e.Err != nil {
		return fmt.Sprintf("%s: %v", e.Message, e.Err)
	}
	return e.Message
}

func (e *CatalogServiceException) Unwrap() error {
	return e.Err
}

// circuitBreakerSuccessWithError é usado para retornar sucesso ao circuit breaker
// mas manter o erro para o cliente (para erros 4xx que não devem contar como falhas)
type circuitBreakerSuccessWithError struct {
	result interface{}
	err    error
}

// CatalogClient é o cliente HTTP para comunicação com o catalog-service
type CatalogClient struct {
	baseURL           string
	timeout           time.Duration
	httpClient        *http.Client
	circuitBreakerMgr *resilience.CircuitBreakerManager
	retryMgr          *resilience.RetryManager
	metrics           metrics.DownstreamMetricsRecorder // Interface para registrar métricas (opcional)
}

// NewCatalogClient cria uma nova instância do CatalogClient
func NewCatalogClient(
	cfg *config.Config,
	circuitBreakerMgr *resilience.CircuitBreakerManager,
	retryMgr *resilience.RetryManager,
) *CatalogClient {
	return NewCatalogClientWithMetrics(cfg, circuitBreakerMgr, retryMgr, nil)
}

// NewCatalogClientWithMetrics cria uma nova instância do CatalogClient com métricas
func NewCatalogClientWithMetrics(
	cfg *config.Config,
	circuitBreakerMgr *resilience.CircuitBreakerManager,
	retryMgr *resilience.RetryManager,
	metricsRecorder metrics.DownstreamMetricsRecorder,
) *CatalogClient {
	timeout := cfg.GetCatalogTimeout()

	return &CatalogClient{
		baseURL:           cfg.Services.Catalog.BaseURL,
		timeout:           timeout,
		httpClient:        &http.Client{Timeout: timeout},
		circuitBreakerMgr: circuitBreakerMgr,
		retryMgr:          retryMgr,
		metrics:           metricsRecorder,
	}
}

// CreateProduct cria um novo produto no catalog-service
// Retorna a resposta HTTP completa para que o handler possa extrair o header Location
func (c *CatalogClient) CreateProduct(ctx context.Context, product *models.Product) (*http.Response, error) {
	log.Printf("[CatalogClient] Enviando requisição para criar produto: %s", product.ID)

	// Serializar produto para JSON
	jsonData, err := json.Marshal(product)
	if err != nil {
		return nil, fmt.Errorf("erro ao serializar produto: %w", err)
	}

	// Construir URL
	endpoint := c.baseURL + "/products"
	reqURL, err := url.Parse(endpoint)
	if err != nil {
		return nil, fmt.Errorf("erro ao construir URL: %w", err)
	}

	// Criar requisição HTTP
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, reqURL.String(), bytes.NewBuffer(jsonData))
	if err != nil {
		return nil, fmt.Errorf("erro ao criar requisição HTTP: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")

	// Função que faz a requisição HTTP com retry
	httpRequestFn := func() (*http.Response, error) {
		start := time.Now()
		resp, err := c.httpClient.Do(req)
		duration := time.Since(start)

		// Registrar métricas de downstream
		if c.metrics != nil {
			if err != nil {
				c.metrics.RecordDownstreamError("catalog", "network_error")
			} else {
				c.metrics.RecordDownstreamRequest("catalog", http.MethodPost, "/products", resp.StatusCode, duration)
			}
		}

		if err != nil {
			return nil, err
		}

		// Verificar status code
		if resp.StatusCode >= 400 {
			// Ler corpo da resposta para incluir no erro
			bodyBytes, _ := io.ReadAll(resp.Body)
			resp.Body.Close()

			// Extrair mensagem de erro do JSON se possível
			errorMsg := extractErrorMessage(bodyBytes, resp.StatusCode)

			// Criar erro com status code
			err := &CatalogServiceException{
				Message: errorMsg,
				Status:  resp.StatusCode,
			}

			// Para status 5xx, retornar erro retentável
			if resp.StatusCode >= 500 {
				return nil, err
			}

			// Para status 4xx, retornar erro permanente (não retentável)
			return nil, err
		}

		return resp, nil
	}

	// Aplicar retry primeiro (retry dentro do circuit breaker)
	retryFn := func() (interface{}, error) {
		return c.retryMgr.ExecuteWithRetryAndHTTPStatus(ctx, resilience.ServiceCatalog, httpRequestFn)
	}

	// Wrapper para o circuit breaker que não conta erros 4xx como falhas
	// Erros 4xx (como 409 para produto duplicado) são erros do cliente, não do servidor
	circuitBreakerFn := func() (interface{}, error) {
		result, err := retryFn()

		// Se for CatalogServiceException com status 4xx, não contar como falha
		// Retornar sucesso para o circuit breaker, mas manter o erro para o cliente
		if catalogErr, ok := err.(*CatalogServiceException); ok {
			if catalogErr.Status >= 400 && catalogErr.Status < 500 {
				// Erro 4xx: retornar sucesso para o circuit breaker
				// mas manter o erro em uma estrutura especial
				return &circuitBreakerSuccessWithError{result: result, err: err}, nil
			}
		}

		return result, err
	}

	// Executar através do circuit breaker (circuit breaker envolve o retry)
	result, err := c.circuitBreakerMgr.Execute(resilience.ServiceCatalog, circuitBreakerFn)

	// Verificar se o resultado contém um erro 4xx que foi mascarado
	if successWithErr, ok := result.(*circuitBreakerSuccessWithError); ok {
		// Extrair resposta e erro da estrutura
		var resp *http.Response
		if successWithErr.result != nil {
			resp = successWithErr.result.(*http.Response)
		}
		return resp, successWithErr.err
	}

	if err != nil {
		// Verificar se é erro do circuit breaker (circuit breaker aberto)
		if err == gobreaker.ErrOpenState {
			return nil, &CatalogServiceException{
				Message: "circuit breaker aberto para catalog-service",
				Err:     err,
			}
		}

		// Se for CatalogServiceException, retornar diretamente
		if catalogErr, ok := err.(*CatalogServiceException); ok {
			return nil, catalogErr
		}

		// Outros erros
		return nil, &CatalogServiceException{
			Message: fmt.Sprintf("erro ao criar produto no catalog-service: %v", err),
			Err:     err,
		}
	}

	// Converter resultado para *http.Response
	if resp, ok := result.(*http.Response); ok {
		log.Printf("[CatalogClient] Produto criado com sucesso: %s", product.ID)
		return resp, nil
	}

	return nil, &CatalogServiceException{
		Message: "erro inesperado: resposta não é *http.Response",
	}
}

// extractErrorMessage tenta extrair a mensagem de erro do corpo JSON da resposta
func extractErrorMessage(bodyBytes []byte, statusCode int) string {
	if len(bodyBytes) == 0 {
		return fmt.Sprintf("erro HTTP %d", statusCode)
	}

	// Tentar parsear JSON
	var errorJSON map[string]interface{}
	if err := json.Unmarshal(bodyBytes, &errorJSON); err == nil {
		// Tentar extrair mensagem
		if msg, ok := errorJSON["message"].(string); ok && msg != "" {
			return msg
		}
		if err, ok := errorJSON["error"].(string); ok && err != "" {
			return err
		}
	}

	// Se não conseguir parsear, usar o corpo completo (limitado)
	bodyStr := string(bodyBytes)
	if len(bodyStr) > 200 {
		return bodyStr[:200] + "..."
	}
	return bodyStr
}
