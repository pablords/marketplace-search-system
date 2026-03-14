package middleware

import (
	"fmt"
	"net/url"
	"strings"
	"sync/atomic"
	"time"

	"github.com/gin-gonic/gin"
	"go.opentelemetry.io/otel/trace"
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

		// Adicionar query string se existir mascarando possíveis PII
		if raw != "" {
			maskedQuery := maskPIIFromQuery(raw)
			fields = append(fields, zap.String("query", maskedQuery))
		}

		// Adicionar request ID se existir
		if requestID := c.GetString("request_id"); requestID != "" {
			fields = append(fields, zap.String("request_id", requestID))
		}

		// Adicionar trace IDs do OpenTelemetry
		spanCtx := trace.SpanFromContext(c.Request.Context()).SpanContext()
		if spanCtx.HasTraceID() {
			fields = append(fields, zap.String("trace_id", spanCtx.TraceID().String()))
		}
		if spanCtx.HasSpanID() {
			fields = append(fields, zap.String("span_id", spanCtx.SpanID().String()))
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

// maskPIIFromQuery mascara campos sensíveis na query string
func maskPIIFromQuery(query string) string {
	values, err := url.ParseQuery(query)
	if err != nil {
		return "***REDACTED_DUE_TO_PARSE_ERROR***"
	}
	
	piiKeys := map[string]bool{
		"email": true, "password": true, "cpf": true, "token": true, 
		"credit_card": true, "user_id": true, "phone": true, "document": true,
	}
	
	for k := range values {
		lowerK := strings.ToLower(k)
		if piiKeys[lowerK] {
			values.Set(k, "***REDACTED***")
		}
	}
	// un-escape para evitar que um encode subsequente faça logs dificeis de ler
	result, err := url.QueryUnescape(values.Encode())
	if err != nil {
		return values.Encode()
	}
	return result
}

