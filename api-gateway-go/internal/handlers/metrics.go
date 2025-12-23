package handlers

import (
	"github.com/gin-gonic/gin"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

// MetricsHandler lida com requisições de métricas Prometheus
// @Summary      Métricas Prometheus
// @Description  Expõe métricas no formato Prometheus
// @Tags         metrics
// @Accept       json
// @Produce      text/plain
// @Success      200  {string}  string  "Métricas Prometheus"
// @Router       /metrics [get]
func MetricsHandler() gin.HandlerFunc {
	// Usar o handler padrão do Prometheus
	handler := promhttp.Handler()
	
	return func(c *gin.Context) {
		handler.ServeHTTP(c.Writer, c.Request)
	}
}

