package middleware

import (
	"github.com/gin-gonic/gin"
	"go.opentelemetry.io/contrib/instrumentation/github.com/gin-gonic/gin/otelgin"
	"go.opentelemetry.io/otel"
)

// TracingMiddleware cria um middleware de tracing usando OpenTelemetry
func TracingMiddleware() gin.HandlerFunc {
	return otelgin.Middleware(
		"api-gateway",
		otelgin.WithTracerProvider(otel.GetTracerProvider()),
	)
}
