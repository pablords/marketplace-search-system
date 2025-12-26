package config

import (
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/spf13/viper"
)

// Config representa a configuração completa da aplicação
type Config struct {
	Application ApplicationConfig `mapstructure:"application"`
	Server      ServerConfig      `mapstructure:"server"`
	Logging     LoggingConfig     `mapstructure:"logging"`
	Management  ManagementConfig  `mapstructure:"management"`
	OpenAPI     OpenAPIConfig     `mapstructure:"openapi"`
	Services    ServicesConfig    `mapstructure:"services"`
}

// ApplicationConfig configurações da aplicação
type ApplicationConfig struct {
	Name        string `mapstructure:"name"`
	Environment string `mapstructure:"environment"`
}

// ServerConfig configurações do servidor HTTP
type ServerConfig struct {
	Port        int    `mapstructure:"port"`
	ContextPath string `mapstructure:"context_path"`
}

// LoggingConfig configurações de logging
type LoggingConfig struct {
	Level  string        `mapstructure:"level"`
	Format string        `mapstructure:"format"`
	File   LogFileConfig `mapstructure:"file"`
}

// LogFileConfig configurações de arquivo de log
type LogFileConfig struct {
	Name       string `mapstructure:"name"`
	MaxSize    int    `mapstructure:"max_size"` // MB
	MaxBackups int    `mapstructure:"max_backups"`
	MaxAge     int    `mapstructure:"max_age"` // days
}

// ManagementConfig configurações de métricas e health checks
type ManagementConfig struct {
	Metrics MetricsConfig `mapstructure:"metrics"`
	Health  HealthConfig  `mapstructure:"health"`
}

// MetricsConfig configurações de métricas
type MetricsConfig struct {
	Enabled bool   `mapstructure:"enabled"`
	Path    string `mapstructure:"path"`
}

// HealthConfig configurações de health check
type HealthConfig struct {
	Path        string `mapstructure:"path"`
	ShowDetails bool   `mapstructure:"show_details"`
}

// OpenAPIConfig configurações do OpenAPI/Swagger
type OpenAPIConfig struct {
	Enabled          bool   `mapstructure:"enabled"`
	DocsPath         string `mapstructure:"docs_path"`
	SwaggerUIPath    string `mapstructure:"swagger_ui_path"`
	OperationsSorter string `mapstructure:"operations_sorter"`
	TagsSorter       string `mapstructure:"tags_sorter"`
}

// ServicesConfig configurações dos serviços downstream
type ServicesConfig struct {
	Catalog ServiceConfig `mapstructure:"catalog"`
	Search  ServiceConfig `mapstructure:"search"`
}

// ServiceConfig configuração de um serviço downstream
type ServiceConfig struct {
	BaseURL        string               `mapstructure:"base_url"`
	Timeout        int                  `mapstructure:"timeout"` // milliseconds
	Retry          RetryConfig          `mapstructure:"retry"`
	CircuitBreaker CircuitBreakerConfig `mapstructure:"circuit_breaker"`
}

// RetryConfig configurações de retry
type RetryConfig struct {
	MaxAttempts int `mapstructure:"max_attempts"`
	MinBackoff  int `mapstructure:"min_backoff"` // milliseconds
}

// CircuitBreakerConfig configurações de circuit breaker
type CircuitBreakerConfig struct {
	FailureRateThreshold    int `mapstructure:"failure_rate_threshold"`      // percentage
	WaitDurationInOpenState int `mapstructure:"wait_duration_in_open_state"` // milliseconds
	SlidingWindowSize       int `mapstructure:"sliding_window_size"`
}

var (
	// GlobalConfig é a instância global da configuração
	GlobalConfig *Config
)

// Load carrega a configuração do arquivo YAML e variáveis de ambiente
func Load(configPath string) (*Config, error) {
	viper.SetConfigType("yaml")

	var configDir string
	var isEnvFile bool

	// Se configPath foi fornecido, verificar se é um arquivo ou diretório
	if configPath != "" {
		info, err := os.Stat(configPath)
		if err != nil {
			return nil, fmt.Errorf("erro ao acessar caminho de configuração: %w", err)
		}

		if info.IsDir() {
			// É um diretório, adicionar como caminho de busca
			configDir = configPath
			viper.SetConfigName("config")
			viper.AddConfigPath(configPath)
			// Ler arquivo de configuração base
			if err := viper.ReadInConfig(); err != nil {
				return nil, fmt.Errorf("erro ao ler arquivo de configuração: %w", err)
			}
		} else {
			// É um arquivo, usar diretamente com SetConfigFile
			configDir = filepath.Dir(configPath)
			filename := filepath.Base(configPath)
			ext := filepath.Ext(filename)
			name := filename[:len(filename)-len(ext)]

			// Verificar se é um arquivo de ambiente (config.xxx.yaml)
			if len(name) > 7 && name[:7] == "config." {
				isEnvFile = true
				// Tentar primeiro carregar config.yaml base do mesmo diretório
				baseConfigPath := filepath.Join(configDir, "config.yaml")
				if _, err := os.Stat(baseConfigPath); err == nil {
					// Arquivo base existe, carregar primeiro
					viper.SetConfigFile(baseConfigPath)
					if err := viper.ReadInConfig(); err != nil {
						return nil, fmt.Errorf("erro ao ler arquivo de configuração base: %w", err)
					}
					// Mesclar com arquivo de ambiente
					envViper := viper.New()
					envViper.SetConfigFile(configPath)
					if err := envViper.ReadInConfig(); err != nil {
						return nil, fmt.Errorf("erro ao ler arquivo de configuração de ambiente: %w", err)
					}
					if err := viper.MergeConfigMap(envViper.AllSettings()); err != nil {
						return nil, fmt.Errorf("erro ao mesclar configuração de ambiente: %w", err)
					}
				} else {
					// Não há arquivo base, usar apenas o arquivo de ambiente
					viper.SetConfigFile(configPath)
					if err := viper.ReadInConfig(); err != nil {
						return nil, fmt.Errorf("erro ao ler arquivo de configuração: %w", err)
					}
				}
			} else {
				// Arquivo normal, usar diretamente
				viper.SetConfigFile(configPath)
				if err := viper.ReadInConfig(); err != nil {
					return nil, fmt.Errorf("erro ao ler arquivo de configuração: %w", err)
				}
			}
		}
	} else {
		// Caminhos padrão
		configDir = "./configs"
		viper.SetConfigName("config")
		viper.AddConfigPath("./configs")
		viper.AddConfigPath("../configs")
		viper.AddConfigPath("../../configs")
		viper.AddConfigPath(".")

		// Ler arquivo de configuração base
		if err := viper.ReadInConfig(); err != nil {
			return nil, fmt.Errorf("erro ao ler arquivo de configuração: %w", err)
		}
	}

	// Se já carregamos um arquivo de ambiente diretamente, não precisamos carregar outro
	if !isEnvFile {
		// Determinar ambiente (development, production, etc.)
		env := getEnv("ENVIRONMENT", "development")

		// Se houver arquivo específico do ambiente, carregar também
		if env != "" {
			envConfigName := fmt.Sprintf("config.%s", env)
			// Criar uma nova instância do Viper para o arquivo de ambiente
			envViper := viper.New()
			envViper.SetConfigType("yaml")
			envViper.SetConfigName(envConfigName)

			// Adicionar os mesmos caminhos de busca
			if configDir != "" {
				envViper.AddConfigPath(configDir)
			}
			envViper.AddConfigPath("./configs")
			envViper.AddConfigPath("../configs")
			envViper.AddConfigPath("../../configs")
			envViper.AddConfigPath(".")

			// Tentar ler arquivo de ambiente (não é erro se não existir)
			if err := envViper.ReadInConfig(); err != nil {
				// Ignorar erro se o arquivo não for encontrado (é opcional)
				// Verificar se é erro de arquivo não encontrado
				if _, ok := err.(viper.ConfigFileNotFoundError); !ok {
					// Se não for erro de arquivo não encontrado, retornar erro
					return nil, fmt.Errorf("erro ao ler arquivo de configuração do ambiente: %w", err)
				}
			} else {
				// Se o arquivo foi encontrado, mesclar com a configuração principal
				if err := viper.MergeConfigMap(envViper.AllSettings()); err != nil {
					return nil, fmt.Errorf("erro ao mesclar configuração do ambiente: %w", err)
				}
			}
		}
	} else {
		// Se já carregamos um arquivo de ambiente, ainda podemos tentar mesclar outro se especificado via ENV
		env := getEnv("ENVIRONMENT", "")
		if env != "" {
			// Verificar se o arquivo carregado já corresponde ao ambiente
			filename := filepath.Base(configPath)
			expectedName := fmt.Sprintf("config.%s.yaml", env)
			if filename != expectedName {
				// Tentar carregar o arquivo de ambiente especificado via ENV
				envConfigName := fmt.Sprintf("config.%s", env)
				envViper := viper.New()
				envViper.SetConfigType("yaml")
				envViper.SetConfigName(envConfigName)
				envViper.AddConfigPath(configDir)
				envViper.AddConfigPath("./configs")
				envViper.AddConfigPath("../configs")
				envViper.AddConfigPath("../../configs")
				envViper.AddConfigPath(".")

				if err := envViper.ReadInConfig(); err == nil {
					// Mesclar se encontrado
					if err := viper.MergeConfigMap(envViper.AllSettings()); err != nil {
						return nil, fmt.Errorf("erro ao mesclar configuração do ambiente: %w", err)
					}
				}
			}
		}
	}

	// Configurar suporte a variáveis de ambiente
	viper.SetEnvPrefix("API_GATEWAY")
	viper.AutomaticEnv()

	// Mapear variáveis de ambiente para chaves de configuração
	bindEnvVars()

	// Deserializar para struct
	var config Config
	if err := viper.Unmarshal(&config); err != nil {
		return nil, fmt.Errorf("erro ao deserializar configuração: %w", err)
	}

	// Validar configuração
	if err := validate(&config); err != nil {
		return nil, fmt.Errorf("erro de validação da configuração: %w", err)
	}

	GlobalConfig = &config
	return &config, nil
}

// bindEnvVars mapeia variáveis de ambiente para chaves de configuração
func bindEnvVars() {
	// Application
	viper.BindEnv("application.environment", "ENVIRONMENT")

	// Server
	viper.BindEnv("server.port", "SERVER_PORT")

	// Logging
	viper.BindEnv("logging.level", "LOG_LEVEL")
	viper.BindEnv("logging.format", "LOG_FORMAT")
	viper.BindEnv("logging.file.name", "LOG_FILE")
	viper.BindEnv("management.health.show_details", "HEALTH_SHOW_DETAILS")

	// Services - Catalog
	viper.BindEnv("services.catalog.base_url", "CATALOG_SERVICE_BASE_URL")
	viper.BindEnv("services.catalog.timeout", "CATALOG_SERVICE_TIMEOUT")
	viper.BindEnv("services.catalog.retry.max_attempts", "CATALOG_SERVICE_RETRY_MAX_ATTEMPTS")
	viper.BindEnv("services.catalog.retry.min_backoff", "CATALOG_SERVICE_RETRY_MIN_BACKOFF")
	viper.BindEnv("services.catalog.circuit_breaker.failure_rate_threshold", "CATALOG_SERVICE_CB_FAILURE_RATE_THRESHOLD")
	viper.BindEnv("services.catalog.circuit_breaker.wait_duration_in_open_state", "CATALOG_SERVICE_CB_WAIT_DURATION")
	viper.BindEnv("services.catalog.circuit_breaker.sliding_window_size", "CATALOG_SERVICE_CB_SLIDING_WINDOW_SIZE")

	// Services - Search
	viper.BindEnv("services.search.base_url", "SEARCH_SERVICE_BASE_URL")
	viper.BindEnv("services.search.timeout", "SEARCH_SERVICE_TIMEOUT")
	viper.BindEnv("services.search.retry.max_attempts", "SEARCH_SERVICE_RETRY_MAX_ATTEMPTS")
	viper.BindEnv("services.search.retry.min_backoff", "SEARCH_SERVICE_RETRY_MIN_BACKOFF")
	viper.BindEnv("services.search.circuit_breaker.failure_rate_threshold", "SEARCH_SERVICE_CB_FAILURE_RATE_THRESHOLD")
	viper.BindEnv("services.search.circuit_breaker.wait_duration_in_open_state", "SEARCH_SERVICE_CB_WAIT_DURATION")
	viper.BindEnv("services.search.circuit_breaker.sliding_window_size", "SEARCH_SERVICE_CB_SLIDING_WINDOW_SIZE")
}

// validate valida a configuração carregada
func validate(config *Config) error {
	// Validar porta do servidor
	if config.Server.Port <= 0 || config.Server.Port > 65535 {
		return fmt.Errorf("porta do servidor inválida: %d", config.Server.Port)
	}

	// Validar timeouts dos serviços
	if config.Services.Catalog.Timeout <= 0 {
		return fmt.Errorf("timeout do catalog service deve ser maior que zero")
	}
	if config.Services.Search.Timeout <= 0 {
		return fmt.Errorf("timeout do search service deve ser maior que zero")
	}

	// Validar configurações de retry
	if config.Services.Catalog.Retry.MaxAttempts < 1 {
		return fmt.Errorf("max_attempts do catalog service deve ser pelo menos 1")
	}
	if config.Services.Search.Retry.MaxAttempts < 1 {
		return fmt.Errorf("max_attempts do search service deve ser pelo menos 1")
	}

	// Validar circuit breaker
	if config.Services.Catalog.CircuitBreaker.FailureRateThreshold < 0 || config.Services.Catalog.CircuitBreaker.FailureRateThreshold > 100 {
		return fmt.Errorf("failure_rate_threshold do catalog service deve estar entre 0 e 100")
	}
	if config.Services.Search.CircuitBreaker.FailureRateThreshold < 0 || config.Services.Search.CircuitBreaker.FailureRateThreshold > 100 {
		return fmt.Errorf("failure_rate_threshold do search service deve estar entre 0 e 100")
	}

	return nil
}

// getEnv obtém uma variável de ambiente ou retorna um valor padrão
func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

// GetTimeoutDuration retorna o timeout como time.Duration para o serviço catalog
func (c *Config) GetCatalogTimeout() time.Duration {
	return time.Duration(c.Services.Catalog.Timeout) * time.Millisecond
}

// GetSearchTimeout retorna o timeout como time.Duration para o serviço search
func (c *Config) GetSearchTimeout() time.Duration {
	return time.Duration(c.Services.Search.Timeout) * time.Millisecond
}

// GetCatalogRetryBackoff retorna o backoff mínimo como time.Duration
func (c *Config) GetCatalogRetryBackoff() time.Duration {
	return time.Duration(c.Services.Catalog.Retry.MinBackoff) * time.Millisecond
}

// GetSearchRetryBackoff retorna o backoff mínimo como time.Duration
func (c *Config) GetSearchRetryBackoff() time.Duration {
	return time.Duration(c.Services.Search.Retry.MinBackoff) * time.Millisecond
}

// GetCatalogCircuitBreakerWaitDuration retorna a duração de espera do circuit breaker como time.Duration
func (c *Config) GetCatalogCircuitBreakerWaitDuration() time.Duration {
	return time.Duration(c.Services.Catalog.CircuitBreaker.WaitDurationInOpenState) * time.Millisecond
}

// GetSearchCircuitBreakerWaitDuration retorna a duração de espera do circuit breaker como time.Duration
func (c *Config) GetSearchCircuitBreakerWaitDuration() time.Duration {
	return time.Duration(c.Services.Search.CircuitBreaker.WaitDurationInOpenState) * time.Millisecond
}
