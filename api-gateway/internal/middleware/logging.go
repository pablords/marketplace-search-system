package middleware

import (
	"fmt"
	"sync/atomic"
	"time"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
)

var requestIDCounter uint64

// LoggingMiddleware cria um middleware de logging estruturado
func LoggingMiddleware(logger *zap.Logger) gin.HandlerFunc {
	return func(c *gin.Context) {
		// Iniciar timer
		start := time.Now()
		path := c.Request.URL.Path
		raw := c.Request.URL.RawQuery

		// Processar requisição
		c.Next()

		// Calcular duração
		latency := time.Since(start)

		// Construir campos de log
		fields := []zap.Field{
			zap.Int("status", c.Writer.Status()),
			zap.String("method", c.Request.Method),
			zap.String("path", path),
			zap.String("ip", c.ClientIP()),
			zap.String("user_agent", c.Request.UserAgent()),
			zap.Duration("latency", latency),
			zap.Int("size", c.Writer.Size()),
		}

		// Adicionar query string se existir
		if raw != "" {
			fields = append(fields, zap.String("query", raw))
		}

		// Adicionar request ID se existir
		if requestID := c.GetString("request_id"); requestID != "" {
			fields = append(fields, zap.String("request_id", requestID))
		}

		// Logar erros se houver
		if len(c.Errors) > 0 {
			fields = append(fields, zap.Strings("errors", c.Errors.Errors()))
			logger.Error("Request completed with errors", fields...)
			return
		}

		// Logar baseado no status code
		switch {
		case c.Writer.Status() >= 500:
			logger.Error("Request failed", fields...)
		case c.Writer.Status() >= 400:
			logger.Warn("Request completed with client error", fields...)
		default:
			logger.Info("Request completed", fields...)
		}
	}
}

// RequestIDMiddleware adiciona um ID único para cada requisição
func RequestIDMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		// Tentar obter request ID do header (se enviado pelo cliente)
		requestID := c.GetHeader("X-Request-ID")
		
		// Se não existir, gerar um novo
		if requestID == "" {
			requestID = generateRequestID()
		}

		// Adicionar ao contexto
		c.Set("request_id", requestID)
		
		// Adicionar ao header de resposta
		c.Header("X-Request-ID", requestID)

		c.Next()
	}
}

// generateRequestID gera um ID único para a requisição
func generateRequestID() string {
	// Usar timestamp + contador atômico para garantir unicidade
	counter := atomic.AddUint64(&requestIDCounter, 1)
	timestamp := time.Now().UnixNano()
	return fmt.Sprintf("%d-%d", timestamp, counter)
}

