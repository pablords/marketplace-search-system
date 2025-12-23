package resilience

import (
	"context"
	"errors"
	"net/http"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"

	"api-gateway-go/internal/config"
)

func TestNewRetryManager(t *testing.T) {
	cfg := &config.Config{
		Services: config.ServicesConfig{
			Catalog: config.ServiceConfig{
				Retry: config.RetryConfig{
					MaxAttempts: 3,
					MinBackoff:  500,
				},
			},
			Search: config.ServiceConfig{
				Retry: config.RetryConfig{
					MaxAttempts: 3,
					MinBackoff:  500,
				},
			},
		},
	}

	manager := NewRetryManager(cfg)
	assert.NotNil(t, manager)
	assert.NotEmpty(t, manager.configs)
}

func TestRetryManager_GetRetryConfig(t *testing.T) {
	cfg := &config.Config{
		Services: config.ServicesConfig{
			Catalog: config.ServiceConfig{
				Retry: config.RetryConfig{
					MaxAttempts: 3,
					MinBackoff:  500,
				},
			},
			Search: config.ServiceConfig{
				Retry: config.RetryConfig{
					MaxAttempts: 5,
					MinBackoff:  1000,
				},
			},
		},
	}

	manager := NewRetryManager(cfg)

	// Testar catalog service
	retryConfig, err := manager.GetRetryConfig(ServiceCatalog)
	assert.NoError(t, err)
	assert.Equal(t, 3, retryConfig.MaxAttempts)
	assert.Equal(t, 500*time.Millisecond, retryConfig.MinBackoff)

	// Testar search service
	retryConfig, err = manager.GetRetryConfig(ServiceSearch)
	assert.NoError(t, err)
	assert.Equal(t, 5, retryConfig.MaxAttempts)
	assert.Equal(t, 1000*time.Millisecond, retryConfig.MinBackoff)

	// Testar serviço inexistente
	_, err = manager.GetRetryConfig("unknown")
	assert.Error(t, err)
}

func TestShouldRetry(t *testing.T) {
	tests := []struct {
		name       string
		err        error
		httpStatus int
		want       bool
	}{
		{
			name:       "erro 5xx deve ser retentável",
			err:        nil,
			httpStatus: 500,
			want:       true,
		},
		{
			name:       "erro 502 deve ser retentável",
			err:        nil,
			httpStatus: 502,
			want:       true,
		},
		{
			name:       "erro 4xx não deve ser retentável",
			err:        nil,
			httpStatus: 400,
			want:       false,
		},
		{
			name:       "erro 404 não deve ser retentável",
			err:        nil,
			httpStatus: 404,
			want:       false,
		},
		{
			name:       "timeout error deve ser retentável",
			err:        errors.New("context deadline exceeded"),
			httpStatus: 0,
			want:       true,
		},
		{
			name:       "erro genérico sem status não deve ser retentável",
			err:        errors.New("generic error"),
			httpStatus: 0,
			want:       false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := ShouldRetry(tt.err, tt.httpStatus)
			assert.Equal(t, tt.want, got)
		})
	}
}

func TestRetryManager_ExecuteWithRetry_Success(t *testing.T) {
	cfg := &config.Config{
		Services: config.ServicesConfig{
			Catalog: config.ServiceConfig{
				Retry: config.RetryConfig{
					MaxAttempts: 3,
					MinBackoff:  100, // Backoff curto para testes rápidos
				},
			},
			Search: config.ServiceConfig{
				Retry: config.RetryConfig{
					MaxAttempts: 3,
					MinBackoff:  100,
				},
			},
		},
	}

	manager := NewRetryManager(cfg)
	ctx := context.Background()

	// Executar função que retorna sucesso imediatamente
	result, err := manager.ExecuteWithRetry(ctx, ServiceCatalog, func() (interface{}, error) {
		return "success", nil
	})

	assert.NoError(t, err)
	assert.Equal(t, "success", result)
}

func TestRetryManager_ExecuteWithRetry_RetryOn5xx(t *testing.T) {
	cfg := &config.Config{
		Services: config.ServicesConfig{
			Catalog: config.ServiceConfig{
				Retry: config.RetryConfig{
					MaxAttempts: 3,
					MinBackoff:  50, // Backoff muito curto para testes rápidos
				},
			},
			Search: config.ServiceConfig{
				Retry: config.RetryConfig{
					MaxAttempts: 3,
					MinBackoff:  50,
				},
			},
		},
	}

	manager := NewRetryManager(cfg)
	ctx := context.Background()

	attempts := 0

	// Executar função que falha duas vezes com 5xx e depois sucede
	result, err := manager.ExecuteWithRetry(ctx, ServiceCatalog, func() (interface{}, error) {
		attempts++
		if attempts < 3 {
			// Simular erro 5xx
			return nil, errors.New("HTTP 500")
		}
		return "success", nil
	})

	// Verificar que houve retry (pelo menos 3 tentativas)
	assert.GreaterOrEqual(t, attempts, 3)
	assert.NoError(t, err)
	assert.Equal(t, "success", result)
}

func TestRetryManager_ExecuteWithRetry_NoRetryOn4xx(t *testing.T) {
	cfg := &config.Config{
		Services: config.ServicesConfig{
			Catalog: config.ServiceConfig{
				Retry: config.RetryConfig{
					MaxAttempts: 3,
					MinBackoff:  50,
				},
			},
			Search: config.ServiceConfig{
				Retry: config.RetryConfig{
					MaxAttempts: 3,
					MinBackoff:  50,
				},
			},
		},
	}

	manager := NewRetryManager(cfg)
	ctx := context.Background()

	attempts := 0

	// Executar função que sempre retorna erro 4xx
	result, err := manager.ExecuteWithRetry(ctx, ServiceCatalog, func() (interface{}, error) {
		attempts++
		return nil, errors.New("HTTP 400")
	})

	// Verificar que não houve retry (apenas 1 tentativa)
	assert.Equal(t, 1, attempts)
	assert.Error(t, err)
	assert.Nil(t, result)
}

func TestRetryManager_ExecuteWithRetry_MaxAttempts(t *testing.T) {
	cfg := &config.Config{
		Services: config.ServicesConfig{
			Catalog: config.ServiceConfig{
				Retry: config.RetryConfig{
					MaxAttempts: 2,
					MinBackoff:  50,
				},
			},
			Search: config.ServiceConfig{
				Retry: config.RetryConfig{
					MaxAttempts: 2,
					MinBackoff:  50,
				},
			},
		},
	}

	manager := NewRetryManager(cfg)
	ctx := context.Background()

	attempts := 0

	// Executar função que sempre falha com 5xx
	result, err := manager.ExecuteWithRetry(ctx, ServiceCatalog, func() (interface{}, error) {
		attempts++
		return nil, errors.New("HTTP 500")
	})

	// Verificar que parou após max attempts
	assert.Equal(t, 2, attempts)
	assert.Error(t, err)
	assert.Nil(t, result)
}

func TestRetryManager_ExecuteWithRetryAndHTTPStatus_Success(t *testing.T) {
	cfg := &config.Config{
		Services: config.ServicesConfig{
			Catalog: config.ServiceConfig{
				Retry: config.RetryConfig{
					MaxAttempts: 3,
					MinBackoff:  50,
				},
			},
			Search: config.ServiceConfig{
				Retry: config.RetryConfig{
					MaxAttempts: 3,
					MinBackoff:  50,
				},
			},
		},
	}

	manager := NewRetryManager(cfg)
	ctx := context.Background()

	// Criar resposta HTTP mock
	mockResponse := &http.Response{
		StatusCode: http.StatusOK,
	}

	// Executar função que retorna sucesso
	resp, err := manager.ExecuteWithRetryAndHTTPStatus(ctx, ServiceCatalog, func() (*http.Response, error) {
		return mockResponse, nil
	})

	assert.NoError(t, err)
	assert.NotNil(t, resp)
	assert.Equal(t, http.StatusOK, resp.StatusCode)
}

func TestRetryManager_ExecuteWithRetryAndHTTPStatus_RetryOn5xx(t *testing.T) {
	cfg := &config.Config{
		Services: config.ServicesConfig{
			Catalog: config.ServiceConfig{
				Retry: config.RetryConfig{
					MaxAttempts: 3,
					MinBackoff:  50,
				},
			},
			Search: config.ServiceConfig{
				Retry: config.RetryConfig{
					MaxAttempts: 3,
					MinBackoff:  50,
				},
			},
		},
	}

	manager := NewRetryManager(cfg)
	ctx := context.Background()

	attempts := 0

	// Executar função que falha duas vezes com 5xx e depois sucede
	resp, err := manager.ExecuteWithRetryAndHTTPStatus(ctx, ServiceCatalog, func() (*http.Response, error) {
		attempts++
		if attempts < 3 {
			return &http.Response{StatusCode: http.StatusInternalServerError}, nil
		}
		return &http.Response{StatusCode: http.StatusOK}, nil
	})

	assert.GreaterOrEqual(t, attempts, 3)
	assert.NoError(t, err)
	assert.NotNil(t, resp)
	assert.Equal(t, http.StatusOK, resp.StatusCode)
}

func TestExtractHTTPStatus(t *testing.T) {
	tests := []struct {
		name string
		err  error
		want int
	}{
		{
			name: "erro com 500",
			err:  errors.New("HTTP 500 error"),
			want: 500,
		},
		{
			name: "erro com 502",
			err:  errors.New("status code: 502"),
			want: 502,
		},
		{
			name: "erro sem status code",
			err:  errors.New("generic error"),
			want: 0,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := extractHTTPStatus(tt.err)
			assert.Equal(t, tt.want, got)
		})
	}
}

