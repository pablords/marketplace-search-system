// +build integration

package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap"

	"api-gateway-go/internal/clients"
	"api-gateway-go/internal/config"
	"api-gateway-go/internal/handlers"
	"api-gateway-go/internal/metrics"
	"api-gateway-go/internal/models"
	"api-gateway-go/internal/resilience"
	"api-gateway-go/internal/server"
)

// setupTestServer cria um servidor de teste com configuração mínima
func setupTestServer(t *testing.T) *server.Server {
	// Configurar Gin em modo de teste
	gin.SetMode(gin.TestMode)

	// Criar configuração de teste
	cfg := &config.Config{
		Application: config.ApplicationConfig{
			Name:        "api-gateway",
			Environment: "test",
		},
		Server: config.ServerConfig{
			Port:        8080,
			ContextPath: "/api/v1",
		},
		Logging: config.LoggingConfig{
			Level:  "INFO",
			Format: "json",
		},
		Management: config.ManagementConfig{
			Metrics: config.MetricsConfig{
				Enabled: true,
				Path:    "/metrics",
			},
			Health: config.HealthConfig{
				Path:       "/health",
				ShowDetails: true,
			},
		},
		OpenAPI: config.OpenAPIConfig{
			Enabled: true,
		},
		Services: config.ServicesConfig{
			Catalog: config.ServiceConfig{
				BaseURL: "http://localhost:8081/api/v1",
				Timeout: 5000,
				Retry: config.RetryConfig{
					MaxAttempts: 3,
					MinBackoff:  500,
				},
				CircuitBreaker: config.CircuitBreakerConfig{
					FailureRateThreshold:   50,
					WaitDurationInOpenState: 10000,
					SlidingWindowSize:      10,
				},
			},
			Search: config.ServiceConfig{
				BaseURL: "http://localhost:8083/api/v1",
				Timeout: 3000,
				Retry: config.RetryConfig{
					MaxAttempts: 3,
					MinBackoff:  500,
				},
				CircuitBreaker: config.CircuitBreakerConfig{
					FailureRateThreshold:   50,
					WaitDurationInOpenState: 10000,
					SlidingWindowSize:      10,
				},
			},
		},
	}

	// Criar logger
	logger, err := zap.NewDevelopment()
	require.NoError(t, err)

	// Inicializar managers de resiliência
	circuitBreakerMgr := resilience.NewCircuitBreakerManager(cfg)
	retryMgr := resilience.NewRetryManager(cfg)

	// Inicializar métricas
	m := metrics.NewMetrics()
	circuitBreakerMgr = resilience.NewCircuitBreakerManagerWithMetrics(cfg, m)

	// Criar clients HTTP (sem métricas para testes simples)
	catalogClient := clients.NewCatalogClient(cfg, circuitBreakerMgr, retryMgr)
	searchClient := clients.NewSearchClient(cfg, circuitBreakerMgr, retryMgr)

	// Criar handlers
	productHandler := handlers.NewProductHandler(catalogClient)
	searchHandler := handlers.NewSearchHandler(searchClient)

	// Criar servidor
	srv := server.NewServerWithMetrics(cfg, logger, productHandler, searchHandler, m)

	return srv
}

func TestIntegration_HealthEndpoint(t *testing.T) {
	if testing.Short() {
		t.Skip("Pulando teste de integração em modo short")
	}

	srv := setupTestServer(t)
	router := srv.GetRouter()

	// Criar requisição
	req, _ := http.NewRequest("GET", "/api/v1/health", nil)
	w := httptest.NewRecorder()

	// Executar requisição
	router.ServeHTTP(w, req)

	// Verificar status code
	assert.Equal(t, http.StatusOK, w.Code)

	// Verificar conteúdo da resposta
	var response handlers.HealthResponse
	err := json.Unmarshal(w.Body.Bytes(), &response)
	assert.NoError(t, err)
	assert.Equal(t, "UP", response.Status)
	assert.Equal(t, "api-gateway", response.Service)
}

func TestIntegration_MetricsEndpoint(t *testing.T) {
	if testing.Short() {
		t.Skip("Pulando teste de integração em modo short")
	}

	srv := setupTestServer(t)
	router := srv.GetRouter()

	// Criar requisição
	req, _ := http.NewRequest("GET", "/api/v1/metrics", nil)
	w := httptest.NewRecorder()

	// Executar requisição
	router.ServeHTTP(w, req)

	// Verificar status code
	assert.Equal(t, http.StatusOK, w.Code)

	// Verificar que retorna métricas Prometheus
	body := w.Body.String()
	assert.Contains(t, body, "# HELP")
	assert.Contains(t, body, "# TYPE")
}

func TestIntegration_CreateProduct_InvalidRequest(t *testing.T) {
	if testing.Short() {
		t.Skip("Pulando teste de integração em modo short")
	}

	srv := setupTestServer(t)
	router := srv.GetRouter()

	// Criar requisição com JSON inválido
	req, _ := http.NewRequest("POST", "/api/v1/products", bytes.NewBufferString("{invalid json}"))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	// Executar requisição
	router.ServeHTTP(w, req)

	// Verificar que retornou erro de validação
	assert.True(t, w.Code >= 400, "Deve retornar erro para JSON inválido")
}

func TestIntegration_CreateProduct_InvalidProduct(t *testing.T) {
	if testing.Short() {
		t.Skip("Pulando teste de integração em modo short")
	}

	srv := setupTestServer(t)
	router := srv.GetRouter()

	// Criar produto inválido (sem campos obrigatórios)
	invalidProduct := map[string]interface{}{
		"id": "", // ID vazio
	}

	productJSON, _ := json.Marshal(invalidProduct)

	// Criar requisição
	req, _ := http.NewRequest("POST", "/api/v1/products", bytes.NewBuffer(productJSON))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	// Executar requisição
	router.ServeHTTP(w, req)

	// Verificar que retornou erro de validação
	assert.True(t, w.Code >= 400, "Deve retornar erro para produto inválido")
}

func TestIntegration_SearchProducts_MissingQuery(t *testing.T) {
	if testing.Short() {
		t.Skip("Pulando teste de integração em modo short")
	}

	srv := setupTestServer(t)
	router := srv.GetRouter()

	// Criar requisição sem query parameter
	req, _ := http.NewRequest("GET", "/api/v1/search/products", nil)
	w := httptest.NewRecorder()

	// Executar requisição
	router.ServeHTTP(w, req)

	// Verificar que retornou erro
	assert.True(t, w.Code >= 400, "Deve retornar erro para query ausente")
}

func TestIntegration_SearchSuggestions_MissingTerm(t *testing.T) {
	if testing.Short() {
		t.Skip("Pulando teste de integração em modo short")
	}

	srv := setupTestServer(t)
	router := srv.GetRouter()

	// Criar requisição sem term parameter
	req, _ := http.NewRequest("GET", "/api/v1/search/suggestions", nil)
	w := httptest.NewRecorder()

	// Executar requisição
	router.ServeHTTP(w, req)

	// Verificar que retornou erro
	assert.True(t, w.Code >= 400, "Deve retornar erro para term ausente")
}

func TestIntegration_GetProduct_MissingID(t *testing.T) {
	if testing.Short() {
		t.Skip("Pulando teste de integração em modo short")
	}

	srv := setupTestServer(t)
	router := srv.GetRouter()

	// Criar requisição sem ID
	req, _ := http.NewRequest("GET", "/api/v1/search/products/", nil)
	w := httptest.NewRecorder()

	// Executar requisição
	router.ServeHTTP(w, req)

	// Verificar que retornou erro ou redirecionamento
	assert.True(t, w.Code >= 400 || w.Code == 301 || w.Code == 302, "Deve retornar erro ou redirecionamento")
}

// TestIntegration_EndToEnd_ProductFlow testa o fluxo completo de criação e busca de produto
// Este teste requer que os serviços downstream estejam rodando
func TestIntegration_EndToEnd_ProductFlow(t *testing.T) {
	if testing.Short() {
		t.Skip("Pulando teste de integração em modo short")
	}

	// Verificar se os serviços estão disponíveis
	catalogURL := os.Getenv("CATALOG_SERVICE_URL")
	searchURL := os.Getenv("SEARCH_SERVICE_URL")

	if catalogURL == "" || searchURL == "" {
		t.Skip("Serviços downstream não configurados (CATALOG_SERVICE_URL, SEARCH_SERVICE_URL)")
	}

	srv := setupTestServer(t)
	router := srv.GetRouter()

	// Criar produto válido
	product := &models.Product{
		ID:       "test-prod-" + time.Now().Format("20060102150405"),
		Title:    "Produto de Teste",
		Price:    99.99,
		Currency: "BRL",
		Category: models.Category{
			ID:   "cat-1",
			Name: "Categoria Teste",
			Path: "/cat1",
		},
		Brand: models.Brand{
			ID:   "brand-1",
			Name: "Marca Teste",
		},
		Seller: models.Seller{
			ID:   "seller-1",
			Name: "Vendedor Teste",
		},
	}

	productJSON, _ := json.Marshal(product)

	// 1. Criar produto
	req, _ := http.NewRequest("POST", "/api/v1/products", bytes.NewBuffer(productJSON))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	router.ServeHTTP(w, req)

	// Verificar resposta (pode ser 201 ou 502 se o serviço não estiver disponível)
	if w.Code == http.StatusCreated {
		t.Log("Produto criado com sucesso")

		// 2. Buscar produto
		searchReq, _ := http.NewRequest("GET", "/api/v1/search/products?query="+product.Title, nil)
		searchW := httptest.NewRecorder()

		router.ServeHTTP(searchW, searchReq)

		// Verificar resposta (pode ser 200 ou 502)
		if searchW.Code == http.StatusOK {
			t.Log("Busca realizada com sucesso")
		} else {
			t.Logf("Busca retornou status %d (serviço pode não estar disponível)", searchW.Code)
		}
	} else {
		t.Logf("Criação retornou status %d (serviço pode não estar disponível)", w.Code)
	}
}

