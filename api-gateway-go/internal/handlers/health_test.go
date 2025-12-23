package handlers

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/assert"
)

func TestHealthHandler(t *testing.T) {
	// Configurar Gin em modo de teste
	gin.SetMode(gin.TestMode)

	// Criar router
	router := gin.New()
	router.GET("/health", HealthHandler())

	// Criar requisição
	req, _ := http.NewRequest("GET", "/health", nil)
	w := httptest.NewRecorder()

	// Executar requisição
	router.ServeHTTP(w, req)

	// Verificar status code
	assert.Equal(t, http.StatusOK, w.Code)

	// Verificar conteúdo da resposta
	var response HealthResponse
	err := json.Unmarshal(w.Body.Bytes(), &response)
	assert.NoError(t, err)
	assert.Equal(t, "UP", response.Status)
	assert.Equal(t, "api-gateway", response.Service)
	assert.NotZero(t, response.Timestamp)

	// Verificar que o timestamp é recente (dentro dos últimos 5 segundos)
	now := time.Now()
	diff := now.Sub(response.Timestamp)
	assert.True(t, diff < 5*time.Second, "Timestamp deve ser recente")
}

