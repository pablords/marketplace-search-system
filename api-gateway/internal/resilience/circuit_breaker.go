package resilience

import (
	"fmt"
	"time"

	"github.com/sony/gobreaker"
	"api-gateway-go/internal/config"
)

// ServiceName representa o nome de um serviço downstream
type ServiceName string

const (
	// ServiceCatalog representa o serviço de catálogo
	ServiceCatalog ServiceName = "catalog"
	// ServiceSearch representa o serviço de busca
	ServiceSearch ServiceName = "search"
)

// CircuitBreakerManager gerencia circuit breakers por serviço
type CircuitBreakerManager struct {
	breakers map[ServiceName]*gobreaker.CircuitBreaker
	config   *config.Config
	metrics  MetricsRecorder // Interface para registrar métricas (opcional)
}

// MetricsRecorder é uma interface para registrar métricas de circuit breaker
type MetricsRecorder interface {
	RecordCircuitBreakerState(service string, state gobreaker.State)
	RecordCircuitBreakerRequest(service string, state gobreaker.State)
	RecordCircuitBreakerFailure(service string)
	RecordCircuitBreakerSuccess(service string)
}

// NewCircuitBreakerManager cria um novo gerenciador de circuit breakers
func NewCircuitBreakerManager(cfg *config.Config) *CircuitBreakerManager {
	return NewCircuitBreakerManagerWithMetrics(cfg, nil)
}

// NewCircuitBreakerManagerWithMetrics cria um novo gerenciador de circuit breakers com métricas
func NewCircuitBreakerManagerWithMetrics(cfg *config.Config, metrics MetricsRecorder) *CircuitBreakerManager {
	manager := &CircuitBreakerManager{
		breakers: make(map[ServiceName]*gobreaker.CircuitBreaker),
		config:   cfg,
		metrics:  metrics,
	}

	// Inicializar circuit breaker para catalog service
	manager.breakers[ServiceCatalog] = manager.createCircuitBreaker(ServiceCatalog, cfg.Services.Catalog.CircuitBreaker)

	// Inicializar circuit breaker para search service
	manager.breakers[ServiceSearch] = manager.createCircuitBreaker(ServiceSearch, cfg.Services.Search.CircuitBreaker)

	// Registrar estado inicial como Closed para garantir visibilidade imediata
	if metrics != nil {
		metrics.RecordCircuitBreakerState(string(ServiceCatalog), gobreaker.StateClosed)
		metrics.RecordCircuitBreakerState(string(ServiceSearch), gobreaker.StateClosed)
	}

	return manager
}

// createCircuitBreaker cria um circuit breaker para um serviço específico
func (cbm *CircuitBreakerManager) createCircuitBreaker(
	serviceName ServiceName,
	cbConfig config.CircuitBreakerConfig,
) *gobreaker.CircuitBreaker {
	// Converter threshold de porcentagem para formato esperado pelo gobreaker
	// gobreaker usa MaxRequests para controlar a janela deslizante
	// e FailureRateThreshold como porcentagem (0.0 a 1.0)
	failureRateThreshold := float64(cbConfig.FailureRateThreshold) / 100.0

	// Configurar settings do circuit breaker
	settings := gobreaker.Settings{
		Name: string(serviceName),
		// ReadyToTrip é chamado quando o circuit breaker está fechado
		// e decide se deve abrir baseado nas estatísticas
		ReadyToTrip: func(counts gobreaker.Counts) bool {
			// Se não há requisições suficientes, não abrir
			if counts.Requests < uint32(cbConfig.SlidingWindowSize) {
				return false
			}
			// Calcular taxa de falha
			failureRate := float64(counts.TotalFailures) / float64(counts.Requests)
			return failureRate >= failureRateThreshold
		},
		// MaxRequests é o número máximo de requisições permitidas
		// quando o circuit breaker está em estado HalfOpen
		MaxRequests: uint32(cbConfig.SlidingWindowSize),
		// Interval é o período de tempo para resetar as contagens
		// Usamos a janela deslizante como intervalo
		Interval: time.Duration(cbConfig.SlidingWindowSize) * time.Second,
		// Timeout é a duração que o circuit breaker permanece aberto
		// antes de tentar entrar em estado HalfOpen
		Timeout: time.Duration(cbConfig.WaitDurationInOpenState) * time.Millisecond,
		// OnStateChange é chamado quando o estado do circuit breaker muda
		OnStateChange: func(name string, from gobreaker.State, to gobreaker.State) {
			// Registrar métricas se disponível
			if cbm.metrics != nil {
				cbm.metrics.RecordCircuitBreakerState(name, to)
			}
		},
	}

	return gobreaker.NewCircuitBreaker(settings)
}

// GetCircuitBreaker retorna o circuit breaker para um serviço específico
func (cbm *CircuitBreakerManager) GetCircuitBreaker(serviceName ServiceName) (*gobreaker.CircuitBreaker, error) {
	breaker, exists := cbm.breakers[serviceName]
	if !exists {
		return nil, fmt.Errorf("circuit breaker não encontrado para o serviço: %s", serviceName)
	}
	return breaker, nil
}

// Execute executa uma função através do circuit breaker do serviço especificado
// Esta é uma função helper que facilita o uso do circuit breaker
func (cbm *CircuitBreakerManager) Execute(serviceName ServiceName, fn func() (interface{}, error)) (interface{}, error) {
	breaker, err := cbm.GetCircuitBreaker(serviceName)
	if err != nil {
		return nil, err
	}
	
	// Registrar estado atual antes da execução
	if cbm.metrics != nil {
		state := breaker.State()
		cbm.metrics.RecordCircuitBreakerRequest(string(serviceName), state)
	}
	
	// Executar função
	result, err := breaker.Execute(fn)
	
	// Registrar resultado
	if cbm.metrics != nil {
		if err != nil {
			cbm.metrics.RecordCircuitBreakerFailure(string(serviceName))
		} else {
			cbm.metrics.RecordCircuitBreakerSuccess(string(serviceName))
		}
	}
	
	return result, err
}

// GetState retorna o estado atual do circuit breaker para um serviço
func (cbm *CircuitBreakerManager) GetState(serviceName ServiceName) (gobreaker.State, error) {
	breaker, err := cbm.GetCircuitBreaker(serviceName)
	if err != nil {
		return gobreaker.StateClosed, err
	}
	return breaker.State(), nil
}

// GetCounts retorna as contagens atuais do circuit breaker para um serviço
func (cbm *CircuitBreakerManager) GetCounts(serviceName ServiceName) (gobreaker.Counts, error) {
	breaker, err := cbm.GetCircuitBreaker(serviceName)
	if err != nil {
		return gobreaker.Counts{}, err
	}
	return breaker.Counts(), nil
}

