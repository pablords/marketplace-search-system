package metrics

import (
	"strconv"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"github.com/sony/gobreaker"
)

// Metrics contém todas as métricas Prometheus
type Metrics struct {
	// Request metrics
	httpRequestsTotal     *prometheus.CounterVec
	httpRequestDuration   *prometheus.HistogramVec
	httpRequestSize       *prometheus.HistogramVec
	httpResponseSize      *prometheus.HistogramVec

	// Error metrics
	httpErrorsTotal       *prometheus.CounterVec

	// Circuit breaker metrics
	circuitBreakerState   *prometheus.GaugeVec
	circuitBreakerRequests *prometheus.CounterVec
	circuitBreakerFailures *prometheus.CounterVec
	circuitBreakerSuccesses *prometheus.CounterVec

	// Downstream service metrics
	downstreamRequestDuration *prometheus.HistogramVec
	downstreamErrorsTotal     *prometheus.CounterVec
}

// NewMetrics cria uma nova instância de métricas
func NewMetrics() *Metrics {
	return &Metrics{
		// Total de requisições HTTP
		httpRequestsTotal: promauto.NewCounterVec(
			prometheus.CounterOpts{
				Name: "api_gateway_http_requests_total",
				Help: "Total de requisições HTTP processadas",
			},
			[]string{"method", "path", "status_code"},
		),

		// Duração das requisições HTTP (latency)
		httpRequestDuration: promauto.NewHistogramVec(
			prometheus.HistogramOpts{
				Name:    "api_gateway_http_request_duration_seconds",
				Help:    "Duração das requisições HTTP em segundos",
				Buckets: []float64{0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0},
			},
			[]string{"method", "path", "status_code"},
		),

		// Tamanho das requisições HTTP
		httpRequestSize: promauto.NewHistogramVec(
			prometheus.HistogramOpts{
				Name:    "api_gateway_http_request_size_bytes",
				Help:    "Tamanho das requisições HTTP em bytes",
				Buckets: prometheus.ExponentialBuckets(100, 10, 7), // 100B até 1GB
			},
			[]string{"method", "path"},
		),

		// Tamanho das respostas HTTP
		httpResponseSize: promauto.NewHistogramVec(
			prometheus.HistogramOpts{
				Name:    "api_gateway_http_response_size_bytes",
				Help:    "Tamanho das respostas HTTP em bytes",
				Buckets: prometheus.ExponentialBuckets(100, 10, 7), // 100B até 1GB
			},
			[]string{"method", "path", "status_code"},
		),

		// Total de erros HTTP
		httpErrorsTotal: promauto.NewCounterVec(
			prometheus.CounterOpts{
				Name: "api_gateway_http_errors_total",
				Help: "Total de erros HTTP",
			},
			[]string{"method", "path", "status_code", "error_type"},
		),

		// Estado do circuit breaker
		circuitBreakerState: promauto.NewGaugeVec(
			prometheus.GaugeOpts{
				Name: "api_gateway_circuit_breaker_state",
				Help: "Estado atual do circuit breaker (0=Closed, 1=Open, 2=HalfOpen)",
			},
			[]string{"service"},
		),

		// Requisições através do circuit breaker
		circuitBreakerRequests: promauto.NewCounterVec(
			prometheus.CounterOpts{
				Name: "api_gateway_circuit_breaker_requests_total",
				Help: "Total de requisições através do circuit breaker",
			},
			[]string{"service", "state"},
		),

		// Falhas do circuit breaker
		circuitBreakerFailures: promauto.NewCounterVec(
			prometheus.CounterOpts{
				Name: "api_gateway_circuit_breaker_failures_total",
				Help: "Total de falhas do circuit breaker",
			},
			[]string{"service"},
		),

		// Sucessos do circuit breaker
		circuitBreakerSuccesses: promauto.NewCounterVec(
			prometheus.CounterOpts{
				Name: "api_gateway_circuit_breaker_successes_total",
				Help: "Total de sucessos do circuit breaker",
			},
			[]string{"service"},
		),

		// Duração das requisições para serviços downstream
		downstreamRequestDuration: promauto.NewHistogramVec(
			prometheus.HistogramOpts{
				Name:    "api_gateway_downstream_request_duration_seconds",
				Help:    "Duração das requisições para serviços downstream em segundos",
				Buckets: []float64{0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0},
			},
			[]string{"service", "method", "endpoint", "status_code"},
		),

		// Erros de serviços downstream
		downstreamErrorsTotal: promauto.NewCounterVec(
			prometheus.CounterOpts{
				Name: "api_gateway_downstream_errors_total",
				Help: "Total de erros de serviços downstream",
			},
			[]string{"service", "error_type"},
		),
	}
}

// RecordHTTPRequest registra uma requisição HTTP
func (m *Metrics) RecordHTTPRequest(method, path string, statusCode int, duration time.Duration, requestSize, responseSize int64) {
	statusCodeStr := formatStatusCode(statusCode)
	
	m.httpRequestsTotal.WithLabelValues(method, path, statusCodeStr).Inc()
	m.httpRequestDuration.WithLabelValues(method, path, statusCodeStr).Observe(duration.Seconds())
	
	if requestSize > 0 {
		m.httpRequestSize.WithLabelValues(method, path).Observe(float64(requestSize))
	}
	
	if responseSize > 0 {
		m.httpResponseSize.WithLabelValues(method, path, statusCodeStr).Observe(float64(responseSize))
	}

	// Registrar erro se status >= 400
	if statusCode >= 400 {
		errorType := getErrorType(statusCode)
		m.httpErrorsTotal.WithLabelValues(method, path, statusCodeStr, errorType).Inc()
	}
}

// RecordCircuitBreakerState registra o estado do circuit breaker
func (m *Metrics) RecordCircuitBreakerState(service string, state gobreaker.State) {
	stateValue := float64(0)
	switch state {
	case gobreaker.StateClosed:
		stateValue = 0
	case gobreaker.StateOpen:
		stateValue = 1
	case gobreaker.StateHalfOpen:
		stateValue = 2
	}
	m.circuitBreakerState.WithLabelValues(service).Set(stateValue)
}

// RecordCircuitBreakerRequest registra uma requisição através do circuit breaker
func (m *Metrics) RecordCircuitBreakerRequest(service string, state gobreaker.State) {
	stateStr := stateToString(state)
	m.circuitBreakerRequests.WithLabelValues(service, stateStr).Inc()
}

// RecordCircuitBreakerFailure registra uma falha do circuit breaker
func (m *Metrics) RecordCircuitBreakerFailure(service string) {
	m.circuitBreakerFailures.WithLabelValues(service).Inc()
}

// RecordCircuitBreakerSuccess registra um sucesso do circuit breaker
func (m *Metrics) RecordCircuitBreakerSuccess(service string) {
	m.circuitBreakerSuccesses.WithLabelValues(service).Inc()
}

// RecordDownstreamRequest registra uma requisição para um serviço downstream
func (m *Metrics) RecordDownstreamRequest(service, method, endpoint string, statusCode int, duration time.Duration) {
	statusCodeStr := formatStatusCode(statusCode)
	m.downstreamRequestDuration.WithLabelValues(service, method, endpoint, statusCodeStr).Observe(duration.Seconds())

	// Registrar erro se status >= 400
	if statusCode >= 400 {
		errorType := getErrorType(statusCode)
		m.downstreamErrorsTotal.WithLabelValues(service, errorType).Inc()
	}
}

// RecordDownstreamError registra um erro de serviço downstream
func (m *Metrics) RecordDownstreamError(service, errorType string) {
	m.downstreamErrorsTotal.WithLabelValues(service, errorType).Inc()
}

// Helper functions

func formatStatusCode(code int) string {
	return strconv.Itoa(code)
}

func getErrorType(statusCode int) string {
	switch {
	case statusCode >= 500:
		return "server_error"
	case statusCode >= 400:
		return "client_error"
	default:
		return "unknown"
	}
}

func stateToString(state gobreaker.State) string {
	switch state {
	case gobreaker.StateClosed:
		return "closed"
	case gobreaker.StateOpen:
		return "open"
	case gobreaker.StateHalfOpen:
		return "half_open"
	default:
		return "unknown"
	}
}

// DownstreamMetricsRecorder é uma interface para registrar métricas de serviços downstream
type DownstreamMetricsRecorder interface {
	RecordDownstreamRequest(service, method, endpoint string, statusCode int, duration time.Duration)
	RecordDownstreamError(service, errorType string)
}

