package tracing

import (
	"context"
	"fmt"
	"os"
	"strings"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	"go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.21.0"
	"go.uber.org/zap"
)

var (
	tracerProvider *trace.TracerProvider
	shutdownFunc   func(context.Context) error
)

// Init inicializa o OpenTelemetry tracing
func Init(logger *zap.Logger) error {
	// Obter configurações do ambiente
	serviceName := getEnv("OTEL_SERVICE_NAME", "api-gateway")
	serviceVersion := getEnv("OTEL_SERVICE_VERSION", "1.0.0")
	otelEndpoint := getEnv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://otel-collector:4317")

	logger.Info("Inicializando OpenTelemetry tracing",
		zap.String("service_name", serviceName),
		zap.String("service_version", serviceVersion),
		zap.String("otel_endpoint", otelEndpoint),
	)

	// Criar resource com atributos do serviço
	res, err := resource.New(
		context.Background(),
		resource.WithAttributes(
			semconv.ServiceNameKey.String(serviceName),
			semconv.ServiceVersionKey.String(serviceVersion),
		),
		resource.WithFromEnv(),
		resource.WithProcess(),
		resource.WithOS(),
		resource.WithContainer(),
		resource.WithHost(),
	)
	if err != nil {
		return fmt.Errorf("falha ao criar resource: %w", err)
	}

	// Configurar exportador OTLP
	// Remover protocolo http:// ou https:// do endpoint para gRPC
	endpoint := otelEndpoint
	if strings.HasPrefix(endpoint, "http://") {
		endpoint = strings.TrimPrefix(endpoint, "http://")
	} else if strings.HasPrefix(endpoint, "https://") {
		endpoint = strings.TrimPrefix(endpoint, "https://")
	}
	
	ctx := context.Background()
	client := otlptracegrpc.NewClient(
		otlptracegrpc.WithEndpoint(endpoint),
		otlptracegrpc.WithInsecure(),
		otlptracegrpc.WithTimeout(10*time.Second),
	)
	exporter, err := otlptrace.New(ctx, client)
	if err != nil {
		return fmt.Errorf("falha ao criar exportador OTLP: %w", err)
	}

	// Criar TracerProvider
	tracerProvider = trace.NewTracerProvider(
		trace.WithBatcher(exporter,
			trace.WithBatchTimeout(1*time.Second),
			trace.WithMaxExportBatchSize(512),
		),
		trace.WithResource(res),
		trace.WithSampler(trace.AlwaysSample()),
	)

	// Configurar propagação
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{},
		propagation.Baggage{},
	))

	// Registrar TracerProvider globalmente
	otel.SetTracerProvider(tracerProvider)

	// Salvar função de shutdown
	shutdownFunc = tracerProvider.Shutdown

	logger.Info("OpenTelemetry tracing inicializado com sucesso")
	return nil
}

// Shutdown encerra o tracing gracefulmente
func Shutdown(ctx context.Context) error {
	if shutdownFunc != nil {
		return shutdownFunc(ctx)
	}
	return nil
}

// GetTracerProvider retorna o TracerProvider configurado
func GetTracerProvider() *trace.TracerProvider {
	return tracerProvider
}

// getEnv obtém variável de ambiente com valor padrão
func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}
