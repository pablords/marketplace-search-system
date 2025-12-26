package handlers

import (
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
)

// HealthResponse representa a resposta do health check
type HealthResponse struct {
	Status    string    `json:"status"`
	Service   string    `json:"service"`
	Timestamp time.Time `json:"timestamp"`
}

// HealthHandler lida com requisições de health check
// @Summary      Health check
// @Description  Verifica o status de saúde do API Gateway
// @Tags         health
// @Accept       json
// @Produce      json
// @Success      200  {object}  HealthResponse  "Status do serviço"
// @Router       /health [get]
func HealthHandler() gin.HandlerFunc {
	return func(c *gin.Context) {
		response := HealthResponse{
			Status:    "UP",
			Service:   "api-gateway",
			Timestamp: time.Now(),
		}

		c.JSON(http.StatusOK, response)
	}
}

