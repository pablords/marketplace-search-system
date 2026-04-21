package clients

import (
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
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
)

// SearchServiceException representa um erro de comunicação com o search-service
type SearchServiceException struct {
	Message string
	Status  int
	Err     error
}

func (e *SearchServiceException) Error() string {
	if e.Err != nil {
		return fmt.Sprintf("%s: %v", e.Message, e.Err)
	}
	return e.Message
}

func (e *SearchServiceException) Unwrap() error {
	return e.Err
}

// SearchClient é o cliente HTTP para comunicação com o search-service
type SearchClient struct {
	baseURL           string
	timeout           time.Duration
	httpClient        *http.Client
	circuitBreakerMgr *resilience.CircuitBreakerManager
	retryMgr          *resilience.RetryManager
	metrics           metrics.DownstreamMetricsRecorder // Interface para registrar métricas (opcional)
}

// NewSearchClient cria uma nova instância do SearchClient
func NewSearchClient(
	cfg *config.Config,
	circuitBreakerMgr *resilience.CircuitBreakerManager,
	retryMgr *resilience.RetryManager,
) *SearchClient {
	return NewSearchClientWithMetrics(cfg, circuitBreakerMgr, retryMgr, nil)
}

// NewSearchClientWithMetrics cria uma nova instância do SearchClient com métricas
func NewSearchClientWithMetrics(
	cfg *config.Config,
	circuitBreakerMgr *resilience.CircuitBreakerManager,
	retryMgr *resilience.RetryManager,
	metricsRecorder metrics.DownstreamMetricsRecorder,
) *SearchClient {
	timeout := cfg.GetSearchTimeout()

	return &SearchClient{
		baseURL:           cfg.Services.Search.BaseURL,
		timeout:           timeout,
		httpClient: &http.Client{
			Timeout:   timeout,
			Transport: otelhttp.NewTransport(http.DefaultTransport),
		},
		circuitBreakerMgr: circuitBreakerMgr,
		retryMgr:          retryMgr,
		metrics:           metricsRecorder,
	}
}

// SearchProducts busca produtos no search-service
func (c *SearchClient) SearchProducts(
	ctx context.Context,
	query string,
	categoryID *string,
	page int,
	size int,
	sort string,
	userID *string,
	rankingDebug bool,
) (*models.SearchResult, error) {
	log.Printf("[SearchClient] Enviando requisição para buscar produtos: query=%s, ranking_debug=%t", query, rankingDebug)

	// Construir URL com query parameters
	endpoint := c.baseURL + "/search/products"
	reqURL, err := url.Parse(endpoint)
	if err != nil {
		return nil, fmt.Errorf("erro ao construir URL: %w", err)
	}

	// Adicionar query parameters
	q := reqURL.Query()
	q.Set("query", query)
	if categoryID != nil && *categoryID != "" {
		q.Set("categoryId", *categoryID)
	}
	q.Set("page", fmt.Sprintf("%d", page))
	q.Set("size", fmt.Sprintf("%d", size))
	if sort != "" {
		q.Set("sort", sort)
	}
	if userID != nil && *userID != "" {
		q.Set("userId", *userID)
	}
	q.Set("ranking_debug", fmt.Sprintf("%t", rankingDebug))
	reqURL.RawQuery = q.Encode()

	// Criar requisição HTTP
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL.String(), nil)
	if err != nil {
		return nil, fmt.Errorf("erro ao criar requisição HTTP: %w", err)
	}

	req.Header.Set("Accept", "application/json")

	// Função que faz a requisição HTTP com retry
	httpRequestFn := func() (interface{}, error) {
		start := time.Now()
		resp, err := c.httpClient.Do(req)
		duration := time.Since(start)

		// Registrar métricas de downstream
		if c.metrics != nil {
			if err != nil {
				c.metrics.RecordDownstreamError("search", "network_error")
			} else {
				c.metrics.RecordDownstreamRequest("search", http.MethodGet, "/search/products", resp.StatusCode, duration)
			}
		}

		if err != nil {
			return nil, err
		}
		defer resp.Body.Close()

		// Ler corpo da resposta
		bodyBytes, err := io.ReadAll(resp.Body)
		if err != nil {
			return nil, fmt.Errorf("erro ao ler resposta: %w", err)
		}

		// Verificar status code
		if resp.StatusCode >= 400 {
			errorMsg := extractErrorMessage(bodyBytes, resp.StatusCode)
			err := &SearchServiceException{
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

		// Deserializar resposta
		var searchResult models.SearchResult
		if err := json.Unmarshal(bodyBytes, &searchResult); err != nil {
			return nil, fmt.Errorf("erro ao deserializar resposta: %w", err)
		}

		return &searchResult, nil
	}

	// Aplicar retry primeiro (retry dentro do circuit breaker)
	retryFn := func() (interface{}, error) {
		return c.retryMgr.ExecuteWithRetry(ctx, resilience.ServiceSearch, httpRequestFn)
	}

	// Executar através do circuit breaker (circuit breaker envolve o retry)
	result, err := c.circuitBreakerMgr.Execute(resilience.ServiceSearch, retryFn)
	if err != nil {
		// Verificar se é erro do circuit breaker (circuit breaker aberto)
		if err == gobreaker.ErrOpenState {
			return nil, &SearchServiceException{
				Message: "circuit breaker aberto para search-service",
				Err:     err,
			}
		}

		// Se for SearchServiceException, retornar diretamente
		if searchErr, ok := err.(*SearchServiceException); ok {
			return nil, searchErr
		}

		// Outros erros
		return nil, &SearchServiceException{
			Message: fmt.Sprintf("erro ao buscar produtos no search-service: %v", err),
			Err:     err,
		}
	}

	// Converter resultado para *models.SearchResult
	if searchResult, ok := result.(*models.SearchResult); ok {
		log.Printf("[SearchClient] Busca realizada com sucesso: totalResults=%d", searchResult.TotalCount)
		return searchResult, nil
	}

	return nil, &SearchServiceException{
		Message: "erro inesperado: resposta não é *models.SearchResult",
	}
}

// GetSuggestions obtém sugestões de busca do search-service
func (c *SearchClient) GetSuggestions(ctx context.Context, term string, limit int) ([]string, error) {
	log.Printf("[SearchClient] Enviando requisição para obter sugestões: term=%s", term)

	// Construir URL com query parameters
	endpoint := c.baseURL + "/search/suggestions"
	reqURL, err := url.Parse(endpoint)
	if err != nil {
		return nil, fmt.Errorf("erro ao construir URL: %w", err)
	}

	// Adicionar query parameters
	q := reqURL.Query()
	q.Set("term", term)
	q.Set("limit", fmt.Sprintf("%d", limit))
	reqURL.RawQuery = q.Encode()

	// Criar requisição HTTP
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL.String(), nil)
	if err != nil {
		return nil, fmt.Errorf("erro ao criar requisição HTTP: %w", err)
	}

	req.Header.Set("Accept", "application/json")

	// Função que faz a requisição HTTP com retry
	httpRequestFn := func() (interface{}, error) {
		resp, err := c.httpClient.Do(req)
		if err != nil {
			return nil, err
		}
		defer resp.Body.Close()

		// Ler corpo da resposta
		bodyBytes, err := io.ReadAll(resp.Body)
		if err != nil {
			return nil, fmt.Errorf("erro ao ler resposta: %w", err)
		}

		// Verificar status code
		if resp.StatusCode >= 400 {
			// Para 4xx, retornar lista vazia (comportamento do Java)
			if resp.StatusCode < 500 {
				return []string{}, nil
			}

			// Para status 5xx, retornar erro retentável
			errorMsg := extractErrorMessage(bodyBytes, resp.StatusCode)
			return nil, &SearchServiceException{
				Message: errorMsg,
				Status:  resp.StatusCode,
			}
		}

		// Deserializar resposta (array de strings)
		var suggestions []string
		if err := json.Unmarshal(bodyBytes, &suggestions); err != nil {
			return nil, fmt.Errorf("erro ao deserializar resposta: %w", err)
		}

		return suggestions, nil
	}

	// Aplicar retry primeiro (retry dentro do circuit breaker)
	retryFn := func() (interface{}, error) {
		return c.retryMgr.ExecuteWithRetry(ctx, resilience.ServiceSearch, httpRequestFn)
	}

	// Executar através do circuit breaker (circuit breaker envolve o retry)
	result, err := c.circuitBreakerMgr.Execute(resilience.ServiceSearch, retryFn)
	if err != nil {
		// Verificar se é erro do circuit breaker (circuit breaker aberto)
		if err == gobreaker.ErrOpenState {
			// Retornar lista vazia em caso de circuit breaker aberto (comportamento do Java)
			log.Printf("[SearchClient] Circuit breaker aberto, retornando lista vazia")
			return []string{}, nil
		}

		// Se for SearchServiceException com status 5xx, já foi tentado retry
		// Em caso de erro, retornar lista vazia (comportamento do Java)
		log.Printf("[SearchClient] Erro ao obter sugestões, retornando lista vazia: %v", err)
		return []string{}, nil
	}

	// Converter resultado para []string
	if suggestions, ok := result.([]string); ok {
		log.Printf("[SearchClient] Sugestões obtidas com sucesso: count=%d", len(suggestions))
		return suggestions, nil
	}

	// Se não conseguir converter, retornar lista vazia
	return []string{}, nil
}

// GetProduct busca um produto específico por ID no search-service
func (c *SearchClient) GetProduct(ctx context.Context, productID string) (*models.Product, error) {
	log.Printf("[SearchClient] Enviando requisição para buscar produto: productId=%s", productID)

	// Construir URL
	endpoint := c.baseURL + "/search/products/" + url.PathEscape(productID)
	reqURL, err := url.Parse(endpoint)
	if err != nil {
		return nil, fmt.Errorf("erro ao construir URL: %w", err)
	}

	// Criar requisição HTTP
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL.String(), nil)
	if err != nil {
		return nil, fmt.Errorf("erro ao criar requisição HTTP: %w", err)
	}

	req.Header.Set("Accept", "application/json")

	// Função que faz a requisição HTTP com retry
	httpRequestFn := func() (interface{}, error) {
		resp, err := c.httpClient.Do(req)
		if err != nil {
			return nil, err
		}
		defer resp.Body.Close()

		// Ler corpo da resposta
		bodyBytes, err := io.ReadAll(resp.Body)
		if err != nil {
			return nil, fmt.Errorf("erro ao ler resposta: %w", err)
		}

		// Verificar status code
		if resp.StatusCode == http.StatusNotFound {
			return nil, &SearchServiceException{
				Message: fmt.Sprintf("produto não encontrado: %s", productID),
				Status:  resp.StatusCode,
			}
		}

		if resp.StatusCode >= 400 {
			errorMsg := extractErrorMessage(bodyBytes, resp.StatusCode)
			err := &SearchServiceException{
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

		// Deserializar resposta
		var product models.Product
		if err := json.Unmarshal(bodyBytes, &product); err != nil {
			return nil, fmt.Errorf("erro ao deserializar resposta: %w", err)
		}

		return &product, nil
	}

	// Aplicar retry primeiro (retry dentro do circuit breaker)
	retryFn := func() (interface{}, error) {
		return c.retryMgr.ExecuteWithRetry(ctx, resilience.ServiceSearch, httpRequestFn)
	}

	// Executar através do circuit breaker (circuit breaker envolve o retry)
	result, err := c.circuitBreakerMgr.Execute(resilience.ServiceSearch, retryFn)
	if err != nil {
		// Verificar se é erro do circuit breaker (circuit breaker aberto)
		if err == gobreaker.ErrOpenState {
			return nil, &SearchServiceException{
				Message: "circuit breaker aberto para search-service",
				Err:     err,
			}
		}

		// Se for SearchServiceException, retornar diretamente
		if searchErr, ok := err.(*SearchServiceException); ok {
			return nil, searchErr
		}

		// Outros erros
		return nil, &SearchServiceException{
			Message: fmt.Sprintf("erro ao buscar produto no search-service: %v", err),
			Err:     err,
		}
	}

	// Converter resultado para *models.Product
	if product, ok := result.(*models.Product); ok {
		log.Printf("[SearchClient] Produto encontrado: %s", productID)
		return product, nil
	}

	return nil, &SearchServiceException{
		Message: "erro inesperado: resposta não é *models.Product",
	}
}
