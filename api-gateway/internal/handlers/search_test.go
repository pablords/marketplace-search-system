package handlers

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/assert"

	"api-gateway-go/internal/clients"
	"api-gateway-go/internal/config"
	"api-gateway-go/internal/middleware"
	"api-gateway-go/internal/models"
	"api-gateway-go/internal/resilience"
)

// createTestSearchClient cria um cliente de teste usando um servidor HTTP mock
func createTestSearchClient(t *testing.T, handler http.HandlerFunc) *clients.SearchClient {
	// Criar servidor HTTP de teste
	server := httptest.NewServer(handler)
	t.Cleanup(func() { server.Close() })

	// Criar configuração de teste
	cfg := &config.Config{
		Services: config.ServicesConfig{
			Search: config.ServiceConfig{
				BaseURL: server.URL,
				Timeout: 3000,
				Retry: config.RetryConfig{
					MaxAttempts: 1, // Sem retry para testes
					MinBackoff:  100,
				},
				CircuitBreaker: config.CircuitBreakerConfig{
					FailureRateThreshold:   50,
					WaitDurationInOpenState: 10000,
					SlidingWindowSize:      10,
				},
			},
		},
	}

	// Criar managers
	circuitBreakerMgr := resilience.NewCircuitBreakerManager(cfg)
	retryMgr := resilience.NewRetryManager(cfg)

	// Criar cliente
	return clients.NewSearchClient(cfg, circuitBreakerMgr, retryMgr)
}

func TestSearchHandler_SearchProducts_Success(t *testing.T) {
	// Configurar Gin em modo de teste
	gin.SetMode(gin.TestMode)

	// Criar handler mock do search service
	mockSearchHandler := func(w http.ResponseWriter, r *http.Request) {
		result := &models.SearchResult{
			Products:    []models.Product{},
			TotalCount:  0,
			PageSize:    20,
			PageNumber:  0,
			TotalPages:  0,
			HasNextPage: false,
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(result)
	}

	// Criar cliente de teste
	searchClient := createTestSearchClient(t, mockSearchHandler)

	// Criar handler
	handler := NewSearchHandler(searchClient)


	// Criar router
	router := gin.New()
	router.GET("/search/products", handler.SearchProducts)

	// Criar requisição
	req, _ := http.NewRequest("GET", "/search/products?query=teste", nil)
	w := httptest.NewRecorder()

	// Executar requisição
	router.ServeHTTP(w, req)

	// Verificar status code
	assert.Equal(t, http.StatusOK, w.Code)
}

func TestSearchHandler_SearchProducts_MissingQuery(t *testing.T) {
	// Configurar Gin em modo de teste
	gin.SetMode(gin.TestMode)

	// Criar handler mock do search service (não deve ser chamado)
	mockSearchHandler := func(w http.ResponseWriter, r *http.Request) {
		t.Error("Search service não deve ser chamado para query ausente")
	}

	// Criar cliente de teste
	searchClient := createTestSearchClient(t, mockSearchHandler)

	// Criar handler
	handler := NewSearchHandler(searchClient)

	// Criar router com middleware de erro e validação
	router := gin.New()
	router.Use(middleware.ErrorHandler())
	router.GET("/search/products", middleware.ValidateQuery(&SearchQuery{}), handler.SearchProducts)

	// Criar requisição sem query parameter
	req, _ := http.NewRequest("GET", "/search/products", nil)
	w := httptest.NewRecorder()

	// Executar requisição
	router.ServeHTTP(w, req)

	// Verificar que retornou erro
	assert.True(t, w.Code >= 400, "Deve retornar erro para query ausente")
}

func TestSearchHandler_GetSuggestions_Success(t *testing.T) {
	// Configurar Gin em modo de teste
	gin.SetMode(gin.TestMode)

	// Criar handler mock do search service
	mockSearchHandler := func(w http.ResponseWriter, r *http.Request) {
		suggestions := []string{"teste", "teste produto", "teste busca"}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(suggestions)
	}

	// Criar cliente de teste
	searchClient := createTestSearchClient(t, mockSearchHandler)

	// Criar handler
	handler := NewSearchHandler(searchClient)


	// Criar router
	router := gin.New()
	router.GET("/search/suggestions", handler.GetSuggestions)

	// Criar requisição
	req, _ := http.NewRequest("GET", "/search/suggestions?term=test", nil)
	w := httptest.NewRecorder()

	// Executar requisição
	router.ServeHTTP(w, req)

	// Verificar status code
	assert.Equal(t, http.StatusOK, w.Code)
}

func TestSearchHandler_GetSuggestions_EmptyResult(t *testing.T) {
	// Configurar Gin em modo de teste
	gin.SetMode(gin.TestMode)

	// Criar handler mock do search service que retorna lista vazia
	mockSearchHandler := func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode([]string{})
	}

	// Criar cliente de teste
	searchClient := createTestSearchClient(t, mockSearchHandler)

	// Criar handler
	handler := NewSearchHandler(searchClient)

	// Criar router
	router := gin.New()
	router.GET("/search/suggestions", handler.GetSuggestions)

	// Criar requisição
	req, _ := http.NewRequest("GET", "/search/suggestions?term=test", nil)
	w := httptest.NewRecorder()

	// Executar requisição
	router.ServeHTTP(w, req)

	// Verificar status code
	assert.Equal(t, http.StatusOK, w.Code)
}

func TestSearchHandler_GetProduct_Success(t *testing.T) {
	// Configurar Gin em modo de teste
	gin.SetMode(gin.TestMode)

	// Criar handler mock do search service
	mockSearchHandler := func(w http.ResponseWriter, r *http.Request) {
		product := &models.Product{
			ID:    "prod-123",
			Title: "Produto Teste",
			Price: 99.99,
			Currency: "BRL",
			Category: models.Category{
				ID:   "cat-1",
				Name: "Categoria 1",
				Path: "/cat1",
			},
			Brand: models.Brand{
				ID:   "brand-1",
				Name: "Marca 1",
			},
			Seller: models.Seller{
				ID:   "seller-1",
				Name: "Vendedor 1",
			},
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(product)
	}

	// Criar cliente de teste
	searchClient := createTestSearchClient(t, mockSearchHandler)

	// Criar handler
	handler := NewSearchHandler(searchClient)


	// Criar router
	router := gin.New()
	router.GET("/search/products/:id", handler.GetProduct)

	// Criar requisição
	req, _ := http.NewRequest("GET", "/search/products/prod-123", nil)
	w := httptest.NewRecorder()

	// Executar requisição
	router.ServeHTTP(w, req)

	// Verificar status code
	assert.Equal(t, http.StatusOK, w.Code)
}

func TestSearchHandler_GetProduct_NotFound(t *testing.T) {
	// Configurar Gin em modo de teste
	gin.SetMode(gin.TestMode)

	// Criar handler mock do search service que retorna 404
	mockSearchHandler := func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		json.NewEncoder(w).Encode(map[string]string{"error": "produto não encontrado"})
	}

	// Criar cliente de teste
	searchClient := createTestSearchClient(t, mockSearchHandler)

	// Criar handler
	handler := NewSearchHandler(searchClient)

	// Criar router com middleware de erro
	router := gin.New()
	router.Use(middleware.ErrorHandler())
	router.GET("/search/products/:id", handler.GetProduct)

	// Criar requisição
	req, _ := http.NewRequest("GET", "/search/products/prod-999", nil)
	w := httptest.NewRecorder()

	// Executar requisição
	router.ServeHTTP(w, req)

	// Verificar que retornou erro (pode ser 404 ou 502 dependendo do mapeamento)
	assert.True(t, w.Code >= 400, "Deve retornar erro para produto não encontrado")
}

