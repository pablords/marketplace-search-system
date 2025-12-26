// @title           API Gateway - Marketplace Search System
// @version         1.0.0
// @description     API Gateway para o sistema de busca de marketplace. Roteia requisições para os serviços de catálogo e busca, com suporte a circuit breaker, retry e validação de requisições.
// @termsOfService  http://swagger.io/terms/

// @contact.name   API Support
// @contact.url    http://www.example.com/support
// @contact.email  support@example.com

// @license.name  Apache 2.0
// @license.url   http://www.apache.org/licenses/LICENSE-2.0.html

// @host      localhost:8080
// @BasePath  /api/v1

// @schemes   http https

package main

import (
	"flag"
	"fmt"
	"log"
	"os"
	"path/filepath"

	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
	"gopkg.in/natefinch/lumberjack.v2"

	"api-gateway-go/internal/clients"
	"api-gateway-go/internal/config"
	"api-gateway-go/internal/handlers"
	"api-gateway-go/internal/metrics"
	"api-gateway-go/internal/resilience"
	"api-gateway-go/internal/server"

	// Importa a documentação Swagger gerada
	_ "api-gateway-go/docs"
)

func main() {
	// Parse command line flags
	configPath := flag.String("config", "", "Caminho para o arquivo de configuração (opcional)")
	flag.Parse()

	// Carregar configuração
	cfg, err := config.Load(*configPath)
	if err != nil {
		log.Fatalf("Erro ao carregar configuração: %v", err)
	}

	// Inicializar logger
	logger, err := initLogger(cfg)
	if err != nil {
		log.Fatalf("Erro ao inicializar logger: %v", err)
	}
	defer logger.Sync()

	logger.Info("Iniciando API Gateway",
		zap.String("name", cfg.Application.Name),
		zap.String("environment", cfg.Application.Environment),
	)

	// Inicializar managers de resiliência
	circuitBreakerMgr := resilience.NewCircuitBreakerManager(cfg)
	retryMgr := resilience.NewRetryManager(cfg)

	logger.Info("Managers de resiliência inicializados")

	// Inicializar métricas
	m := metrics.NewMetrics()
	logger.Info("Métricas inicializadas")

	// Atualizar circuit breaker manager com métricas
	// Nota: O circuit breaker manager já foi criado, então precisamos recriar com métricas
	// ou atualizar a referência. Por simplicidade, vamos recriar.
	circuitBreakerMgr = resilience.NewCircuitBreakerManagerWithMetrics(cfg, m)
	logger.Info("Circuit breaker manager atualizado com métricas")

	// Criar clients HTTP com métricas
	catalogClient := clients.NewCatalogClientWithMetrics(cfg, circuitBreakerMgr, retryMgr, m)
	searchClient := clients.NewSearchClientWithMetrics(cfg, circuitBreakerMgr, retryMgr, m)

	logger.Info("Clientes HTTP criados")

	// Criar handlers
	productHandler := handlers.NewProductHandler(catalogClient)
	searchHandler := handlers.NewSearchHandler(searchClient)

	logger.Info("Handlers criados")

	// Criar e iniciar servidor com a mesma instância de métricas
	srv := server.NewServerWithMetrics(cfg, logger, productHandler, searchHandler, m)

	logger.Info("Servidor configurado, iniciando...")

	// Iniciar servidor (bloqueia até receber sinal de shutdown)
	if err := srv.Start(); err != nil {
		logger.Error("Erro ao iniciar ou encerrar servidor", zap.Error(err))
		os.Exit(1)
	}

	logger.Info("API Gateway encerrado com sucesso")
}

// initLogger inicializa o logger zap baseado na configuração
func initLogger(cfg *config.Config) (*zap.Logger, error) {
	// Determinar nível de log
	var level zapcore.Level
	switch cfg.Logging.Level {
	case "DEBUG":
		level = zapcore.DebugLevel
	case "INFO":
		level = zapcore.InfoLevel
	case "WARN":
		level = zapcore.WarnLevel
	case "ERROR":
		level = zapcore.ErrorLevel
	default:
		level = zapcore.InfoLevel
	}

	// Configurar encoder
	var encoderConfig zapcore.EncoderConfig
	if cfg.Application.Environment == "production" {
		encoderConfig = zap.NewProductionEncoderConfig()
	} else {
		encoderConfig = zap.NewDevelopmentEncoderConfig()
	}
	encoderConfig.EncodeTime = zapcore.ISO8601TimeEncoder

	// Determinar formato (json ou text)
	var encoder zapcore.Encoder
	if cfg.Logging.Format == "json" {
		encoder = zapcore.NewJSONEncoder(encoderConfig)
	} else {
		encoder = zapcore.NewConsoleEncoder(encoderConfig)
	}

	// Configurar outputs
	var writeSyncer zapcore.WriteSyncer

	// Se houver configuração de arquivo, usar lumberjack para rotação
	if cfg.Logging.File.Name != "" {
		// Criar diretório de logs se não existir
		logDir := filepath.Dir(cfg.Logging.File.Name)
		if logDir != "." && logDir != "" {
			if err := os.MkdirAll(logDir, 0755); err != nil {
				return nil, fmt.Errorf("erro ao criar diretório de logs: %w", err)
			}
		}

		// Configurar lumberjack para rotação de logs
		lumberjackLogger := &lumberjack.Logger{
			Filename:   cfg.Logging.File.Name,
			MaxSize:    cfg.Logging.File.MaxSize, // MB
			MaxBackups: cfg.Logging.File.MaxBackups,
			MaxAge:     cfg.Logging.File.MaxAge, // days
			Compress:   true,
		}

		// Em desenvolvimento, também escrever no console
		if cfg.Application.Environment != "production" {
			writeSyncer = zapcore.NewMultiWriteSyncer(
				zapcore.AddSync(lumberjackLogger),
				zapcore.AddSync(os.Stdout),
			)
		} else {
			writeSyncer = zapcore.AddSync(lumberjackLogger)
		}
	} else {
		// Sem arquivo, apenas stdout
		writeSyncer = zapcore.AddSync(os.Stdout)
	}

	// Criar core
	core := zapcore.NewCore(encoder, writeSyncer, level)

	// Criar logger com opções adicionais
	options := []zap.Option{
		zap.AddCaller(),
		zap.AddStacktrace(zapcore.ErrorLevel),
	}

	logger := zap.New(core, options...)

	return logger, nil
}
