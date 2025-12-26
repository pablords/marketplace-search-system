package server

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"

	"api-gateway-go/internal/config"
	"api-gateway-go/internal/handlers"
	"api-gateway-go/internal/middleware"
	"api-gateway-go/internal/metrics"
	"api-gateway-go/internal/models"
	"api-gateway-go/pkg/openapi"
)

// Server representa o servidor HTTP
type Server struct {
	router     *gin.Engine
	httpServer *http.Server
	logger     *zap.Logger
	config     *config.Config
	metrics    *metrics.Metrics
}

// NewServer cria uma nova instância do servidor
func NewServer(
	cfg *config.Config,
	logger *zap.Logger,
	catalogClient *handlers.ProductHandler,
	searchClient *handlers.SearchHandler,
) *Server {
	return NewServerWithMetrics(cfg, logger, catalogClient, searchClient, nil)
}

// NewServerWithMetrics cria uma nova instância do servidor com métricas
func NewServerWithMetrics(
	cfg *config.Config,
	logger *zap.Logger,
	catalogClient *handlers.ProductHandler,
	searchClient *handlers.SearchHandler,
	m *metrics.Metrics,
) *Server {
	// Configurar modo do Gin baseado no ambiente
	if cfg.Application.Environment == "production" {
		gin.SetMode(gin.ReleaseMode)
	} else {
		gin.SetMode(gin.DebugMode)
	}

	// Criar router
	router := gin.New()

	// Inicializar métricas se não fornecida
	if m == nil {
		m = metrics.NewMetrics()
	}

	// Aplicar middlewares globais (ordem importa)
	// 1. Recovery middleware (captura panics)
	router.Use(gin.Recovery())

	// 2. Request ID middleware
	router.Use(middleware.RequestIDMiddleware())

	// 3. Metrics middleware (deve ser antes do logging para capturar tudo)
	router.Use(middleware.MetricsMiddleware(m))

	// 4. Logging middleware
	router.Use(middleware.LoggingMiddleware(logger))

	// 5. Error handler middleware (deve ser antes das rotas)
	router.Use(middleware.ErrorHandler())

	// Configurar rotas
	setupRoutes(router, cfg, catalogClient, searchClient, m)

	// Criar servidor HTTP
	addr := fmt.Sprintf(":%d", cfg.Server.Port)
	httpServer := &http.Server{
		Addr:         addr,
		Handler:      router,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	return &Server{
		router:     router,
		httpServer: httpServer,
		logger:     logger,
		config:     cfg,
		metrics:    m,
	}
}

// setupRoutes configura todas as rotas da aplicação
func setupRoutes(
	router *gin.Engine,
	cfg *config.Config,
	productHandler *handlers.ProductHandler,
	searchHandler *handlers.SearchHandler,
	m *metrics.Metrics,
) {
	// Context path
	contextPath := cfg.Server.ContextPath
	api := router.Group(contextPath)

	// Health check endpoint
	healthPath := cfg.Management.Health.Path
	// Remover leading slash se existir (já está no context path)
	if len(healthPath) > 0 && healthPath[0] == '/' {
		healthPath = healthPath[1:]
	}
	// Se o path estiver vazio, usar "health" como padrão
	if healthPath == "" {
		healthPath = "health"
	}
	api.GET(healthPath, handlers.HealthHandler())

	// Product endpoints
	products := api.Group("/products")
	{
		// POST /api/v1/products - Criar produto
		// Usar middleware de validação JSON para validar o Product
		products.POST("", middleware.ValidateJSON(&models.Product{}), productHandler.CreateProduct)
	}

	// Search endpoints
	search := api.Group("/search")
	{
		// GET /api/v1/search/products - Buscar produtos
		search.GET("/products", middleware.ValidateQuery(&handlers.SearchQuery{}), searchHandler.SearchProducts)

		// GET /api/v1/search/products/:id - Obter produto específico
		search.GET("/products/:id", searchHandler.GetProduct)

		// GET /api/v1/search/suggestions - Obter sugestões
		search.GET("/suggestions", middleware.ValidateQuery(&handlers.SuggestionsQuery{}), searchHandler.GetSuggestions)
	}

	// Endpoint de métricas Prometheus
	if cfg.Management.Metrics.Enabled {
		metricsPath := cfg.Management.Metrics.Path
		// Remover leading slash se existir
		if len(metricsPath) > 0 && metricsPath[0] == '/' {
			metricsPath = metricsPath[1:]
		}
		// Se o path estiver vazio, usar "metrics" como padrão
		if metricsPath == "" {
			metricsPath = "metrics"
		}
		api.GET(metricsPath, handlers.MetricsHandler())
	}

	// Endpoints OpenAPI/Swagger
	if cfg.OpenAPI.Enabled {
		openapi.SetupSwagger(router, cfg.OpenAPI.DocsPath, cfg.OpenAPI.SwaggerUIPath)
	}
}

// Start inicia o servidor HTTP
func (s *Server) Start() error {
	s.logger.Info("Iniciando servidor HTTP",
		zap.Int("port", s.config.Server.Port),
		zap.String("context_path", s.config.Server.ContextPath),
		zap.String("environment", s.config.Application.Environment),
	)

	// Canal para receber sinais do sistema
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM, syscall.SIGINT)

	// Iniciar servidor em goroutine
	serverErrChan := make(chan error, 1)
	go func() {
		if err := s.httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			serverErrChan <- err
		}
	}()

	// Aguardar sinal de shutdown ou erro do servidor
	select {
	case err := <-serverErrChan:
		s.logger.Error("Erro ao iniciar servidor", zap.Error(err))
		return err
	case sig := <-sigChan:
		s.logger.Info("Sinal de shutdown recebido", zap.String("signal", sig.String()))
		return s.Shutdown(context.Background())
	}
}

// Shutdown realiza graceful shutdown do servidor
func (s *Server) Shutdown(ctx context.Context) error {
	s.logger.Info("Iniciando graceful shutdown do servidor")

	// Criar contexto com timeout para shutdown
	shutdownCtx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()

	// Tentar fazer shutdown graceful
	if err := s.httpServer.Shutdown(shutdownCtx); err != nil {
		s.logger.Error("Erro durante graceful shutdown", zap.Error(err))
		// Se o shutdown falhar, forçar fechamento
		if closeErr := s.httpServer.Close(); closeErr != nil {
			s.logger.Error("Erro ao forçar fechamento do servidor", zap.Error(closeErr))
			return fmt.Errorf("erro durante shutdown: %w, erro ao forçar fechamento: %v", err, closeErr)
		}
		return err
	}

	s.logger.Info("Servidor encerrado com sucesso")
	return nil
}

// GetRouter retorna o router Gin (útil para testes)
func (s *Server) GetRouter() *gin.Engine {
	return s.router
}

