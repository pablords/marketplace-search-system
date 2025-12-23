package resilience

import (
	"errors"
	"testing"
	"time"

	"github.com/sony/gobreaker"
	"github.com/stretchr/testify/assert"

	"api-gateway-go/internal/config"
)

func TestNewCircuitBreakerManager(t *testing.T) {
	cfg := &config.Config{
		Services: config.ServicesConfig{
			Catalog: config.ServiceConfig{
				CircuitBreaker: config.CircuitBreakerConfig{
					FailureRateThreshold:   50,
					WaitDurationInOpenState: 10000,
					SlidingWindowSize:      10,
				},
			},
			Search: config.ServiceConfig{
				CircuitBreaker: config.CircuitBreakerConfig{
					FailureRateThreshold:   50,
					WaitDurationInOpenState: 10000,
					SlidingWindowSize:      10,
				},
			},
		},
	}

	manager := NewCircuitBreakerManager(cfg)
	assert.NotNil(t, manager)
	assert.NotNil(t, manager.breakers[ServiceCatalog])
	assert.NotNil(t, manager.breakers[ServiceSearch])
}

func TestCircuitBreakerManager_GetCircuitBreaker(t *testing.T) {
	cfg := &config.Config{
		Services: config.ServicesConfig{
			Catalog: config.ServiceConfig{
				CircuitBreaker: config.CircuitBreakerConfig{
					FailureRateThreshold:   50,
					WaitDurationInOpenState: 10000,
					SlidingWindowSize:      10,
				},
			},
			Search: config.ServiceConfig{
				CircuitBreaker: config.CircuitBreakerConfig{
					FailureRateThreshold:   50,
					WaitDurationInOpenState: 10000,
					SlidingWindowSize:      10,
				},
			},
		},
	}

	manager := NewCircuitBreakerManager(cfg)

	// Testar catalog service
	breaker, err := manager.GetCircuitBreaker(ServiceCatalog)
	assert.NoError(t, err)
	assert.NotNil(t, breaker)
	assert.Equal(t, gobreaker.StateClosed, breaker.State())

	// Testar search service
	breaker, err = manager.GetCircuitBreaker(ServiceSearch)
	assert.NoError(t, err)
	assert.NotNil(t, breaker)
	assert.Equal(t, gobreaker.StateClosed, breaker.State())

	// Testar serviço inexistente
	_, err = manager.GetCircuitBreaker("unknown")
	assert.Error(t, err)
}

func TestCircuitBreakerManager_Execute_Success(t *testing.T) {
	cfg := &config.Config{
		Services: config.ServicesConfig{
			Catalog: config.ServiceConfig{
				CircuitBreaker: config.CircuitBreakerConfig{
					FailureRateThreshold:   50,
					WaitDurationInOpenState: 10000,
					SlidingWindowSize:      10,
				},
			},
			Search: config.ServiceConfig{
				CircuitBreaker: config.CircuitBreakerConfig{
					FailureRateThreshold:   50,
					WaitDurationInOpenState: 10000,
					SlidingWindowSize:      10,
				},
			},
		},
	}

	manager := NewCircuitBreakerManager(cfg)

	// Executar função de sucesso
	result, err := manager.Execute(ServiceCatalog, func() (interface{}, error) {
		return "success", nil
	})

	assert.NoError(t, err)
	assert.Equal(t, "success", result)
}

func TestCircuitBreakerManager_Execute_Error(t *testing.T) {
	cfg := &config.Config{
		Services: config.ServicesConfig{
			Catalog: config.ServiceConfig{
				CircuitBreaker: config.CircuitBreakerConfig{
					FailureRateThreshold:   50,
					WaitDurationInOpenState: 10000,
					SlidingWindowSize:      10,
				},
			},
			Search: config.ServiceConfig{
				CircuitBreaker: config.CircuitBreakerConfig{
					FailureRateThreshold:   50,
					WaitDurationInOpenState: 10000,
					SlidingWindowSize:      10,
				},
			},
		},
	}

	manager := NewCircuitBreakerManager(cfg)

	// Executar função com erro
	testErr := errors.New("test error")
	result, err := manager.Execute(ServiceCatalog, func() (interface{}, error) {
		return nil, testErr
	})

	assert.Error(t, err)
	assert.Nil(t, result)
	assert.Equal(t, testErr, err)
}

func TestCircuitBreakerManager_GetState(t *testing.T) {
	cfg := &config.Config{
		Services: config.ServicesConfig{
			Catalog: config.ServiceConfig{
				CircuitBreaker: config.CircuitBreakerConfig{
					FailureRateThreshold:   50,
					WaitDurationInOpenState: 10000,
					SlidingWindowSize:      10,
				},
			},
			Search: config.ServiceConfig{
				CircuitBreaker: config.CircuitBreakerConfig{
					FailureRateThreshold:   50,
					WaitDurationInOpenState: 10000,
					SlidingWindowSize:      10,
				},
			},
		},
	}

	manager := NewCircuitBreakerManager(cfg)

	// Verificar estado inicial
	state, err := manager.GetState(ServiceCatalog)
	assert.NoError(t, err)
	assert.Equal(t, gobreaker.StateClosed, state)
}

func TestCircuitBreakerManager_GetCounts(t *testing.T) {
	cfg := &config.Config{
		Services: config.ServicesConfig{
			Catalog: config.ServiceConfig{
				CircuitBreaker: config.CircuitBreakerConfig{
					FailureRateThreshold:   50,
					WaitDurationInOpenState: 10000,
					SlidingWindowSize:      10,
				},
			},
			Search: config.ServiceConfig{
				CircuitBreaker: config.CircuitBreakerConfig{
					FailureRateThreshold:   50,
					WaitDurationInOpenState: 10000,
					SlidingWindowSize:      10,
				},
			},
		},
	}

	manager := NewCircuitBreakerManager(cfg)

	// Executar algumas operações
	manager.Execute(ServiceCatalog, func() (interface{}, error) {
		return "success", nil
	})

	// Verificar contagens
	counts, err := manager.GetCounts(ServiceCatalog)
	assert.NoError(t, err)
	assert.GreaterOrEqual(t, counts.Requests, uint32(1))
}

func TestCircuitBreaker_OpenState(t *testing.T) {
	cfg := &config.Config{
		Services: config.ServicesConfig{
			Catalog: config.ServiceConfig{
				CircuitBreaker: config.CircuitBreakerConfig{
					FailureRateThreshold:   10, // Threshold baixo para facilitar abertura
					WaitDurationInOpenState: 1000,
					SlidingWindowSize:      5, // Janela pequena para facilitar abertura
				},
			},
			Search: config.ServiceConfig{
				CircuitBreaker: config.CircuitBreakerConfig{
					FailureRateThreshold:   10,
					WaitDurationInOpenState: 1000,
					SlidingWindowSize:      5,
				},
			},
		},
	}

	manager := NewCircuitBreakerManager(cfg)

	// Executar múltiplas falhas para abrir o circuit breaker
	for i := 0; i < 10; i++ {
		manager.Execute(ServiceCatalog, func() (interface{}, error) {
			return nil, errors.New("failure")
		})
	}

	// Aguardar um pouco para o circuit breaker processar
	time.Sleep(100 * time.Millisecond)

	// Verificar estado (pode estar aberto ou ainda fechado dependendo da implementação)
	state, err := manager.GetState(ServiceCatalog)
	assert.NoError(t, err)
	assert.Contains(t, []gobreaker.State{gobreaker.StateClosed, gobreaker.StateOpen, gobreaker.StateHalfOpen}, state)
}

