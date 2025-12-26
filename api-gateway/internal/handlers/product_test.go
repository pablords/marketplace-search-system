package handlers

import (
	"bytes"
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

// createTestCatalogClient cria um cliente de teste usando um servidor HTTP mock
func createTestCatalogClient(t *testing.T, handler http.HandlerFunc) *clients.CatalogClient {
	// Criar servidor HTTP de teste
	server := httptest.NewServer(handler)
	t.Cleanup(func() { server.Close() })

	// Criar configuração de teste
	cfg := &config.Config{
		Services: config.ServicesConfig{
			Catalog: config.ServiceConfig{
				BaseURL: server.URL,
				Timeout: 5000,
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
	return clients.NewCatalogClient(cfg, circuitBreakerMgr, retryMgr)
}

func TestProductHandler_CreateProduct_Success(t *testing.T) {
	// Configurar Gin em modo de teste
	gin.SetMode(gin.TestMode)

	// Criar handler mock do catalog service
	mockCatalogHandler := func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Location", "/api/v1/products/prod-123")
		w.WriteHeader(http.StatusCreated)
	}

	// Criar cliente de teste
	catalogClient := createTestCatalogClient(t, mockCatalogHandler)

	// Criar handler
	handler := NewProductHandler(catalogClient)

	// Criar produto de teste
	product := &models.Product{
		ID:       "prod-123",
		Title:    "Produto Teste",
		Price:    99.99,
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

	// Criar router
	router := gin.New()
	router.POST("/products", handler.CreateProduct)

	// Serializar produto para JSON
	productJSON, _ := json.Marshal(product)

	// Criar requisição
	req, _ := http.NewRequest("POST", "/products", bytes.NewBuffer(productJSON))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	// Executar requisição
	router.ServeHTTP(w, req)

	// Verificar status code
	assert.Equal(t, http.StatusCreated, w.Code)

	// Verificar header Location
	assert.Equal(t, "/api/v1/products/prod-123", w.Header().Get("Location"))
}

func TestProductHandler_CreateProduct_ServiceError(t *testing.T) {
	// Configurar Gin em modo de teste
	gin.SetMode(gin.TestMode)

	// Criar handler mock do catalog service que retorna erro
	mockCatalogHandler := func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadGateway)
		json.NewEncoder(w).Encode(map[string]string{"error": "Service unavailable"})
	}

	// Criar cliente de teste
	catalogClient := createTestCatalogClient(t, mockCatalogHandler)

	// Criar handler
	handler := NewProductHandler(catalogClient)

	// Criar produto de teste
	product := &models.Product{
		ID:       "prod-123",
		Title:    "Produto Teste",
		Price:    99.99,
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

	// Criar router com middleware de erro
	router := gin.New()
	router.Use(middleware.ErrorHandler())
	router.POST("/products", handler.CreateProduct)

	// Serializar produto para JSON
	productJSON, _ := json.Marshal(product)

	// Criar requisição
	req, _ := http.NewRequest("POST", "/products", bytes.NewBuffer(productJSON))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	// Executar requisição
	router.ServeHTTP(w, req)

	// Verificar que retornou erro (pode ser 502 ou outro código de erro)
	assert.True(t, w.Code >= 400, "Deve retornar erro do serviço")
}

func TestProductHandler_CreateProduct_InvalidJSON(t *testing.T) {
	// Configurar Gin em modo de teste
	gin.SetMode(gin.TestMode)

	// Criar handler mock do catalog service (não deve ser chamado)
	mockCatalogHandler := func(w http.ResponseWriter, r *http.Request) {
		t.Error("Catalog service não deve ser chamado para JSON inválido")
	}

	// Criar cliente de teste
	catalogClient := createTestCatalogClient(t, mockCatalogHandler)

	// Criar handler
	handler := NewProductHandler(catalogClient)

	// Criar router com middleware de erro e validação
	router := gin.New()
	router.Use(middleware.ErrorHandler())
	router.POST("/products", middleware.ValidateJSON(&models.Product{}), handler.CreateProduct)

	// Criar requisição com JSON inválido
	req, _ := http.NewRequest("POST", "/products", bytes.NewBufferString("{invalid json"))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	// Executar requisição
	router.ServeHTTP(w, req)

	// Verificar que retornou erro (status 400 ou 500)
	// O middleware de validação deve capturar o erro de JSON inválido
	if w.Code < 400 {
		t.Logf("Status code recebido: %d, Body: %s", w.Code, w.Body.String())
	}
	assert.True(t, w.Code >= 400, "Deve retornar erro para JSON inválido")
}

