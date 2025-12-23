package middleware

import (
	"time"

	"github.com/gin-gonic/gin"
	"api-gateway-go/internal/metrics"
)

// MetricsMiddleware cria um middleware para coletar métricas Prometheus
func MetricsMiddleware(m *metrics.Metrics) gin.HandlerFunc {
	return func(c *gin.Context) {
		// Iniciar timer
		start := time.Now()

		// Obter tamanho da requisição
		requestSize := c.Request.ContentLength
		if requestSize < 0 {
			requestSize = 0
		}

		// Processar requisição
		c.Next()

		// Calcular duração
		duration := time.Since(start)

		// Obter tamanho da resposta
		responseSize := int64(c.Writer.Size())
		statusCode := c.Writer.Status()
		method := c.Request.Method
		path := normalizePath(c.FullPath())

		// Registrar métricas
		m.RecordHTTPRequest(method, path, statusCode, duration, requestSize, responseSize)
	}
}

// normalizePath normaliza o path para métricas (remove IDs dinâmicos)
func normalizePath(path string) string {
	if path == "" {
		return "unknown"
	}
	// O path já vem normalizado do Gin (com :id, etc.)
	// Retornar como está para manter consistência
	return path
}

// GetMetrics retorna a instância global de métricas
var globalMetrics *metrics.Metrics

// InitMetrics inicializa as métricas globais
func InitMetrics() *metrics.Metrics {
	if globalMetrics == nil {
		globalMetrics = metrics.NewMetrics()
	}
	return globalMetrics
}

// GetMetrics retorna as métricas globais
func GetMetrics() *metrics.Metrics {
	if globalMetrics == nil {
		return InitMetrics()
	}
	return globalMetrics
}

