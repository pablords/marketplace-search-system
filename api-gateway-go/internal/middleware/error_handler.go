package middleware

import (
	"net/http"
	"time"

	"github.com/gin-gonic/gin"

	"api-gateway-go/internal/clients"
	"api-gateway-go/internal/models"
)

// ErrorHandler é um middleware que captura panics e erros e os converte em respostas JSON padronizadas
func ErrorHandler() gin.HandlerFunc {
	return func(c *gin.Context) {
		// Recuperar de panics
		defer func() {
			if err := recover(); err != nil {
				// Log do panic (será logado pelo middleware de logging se configurado)
				handleError(c, http.StatusInternalServerError, "Erro interno do servidor", err)
			}
		}()

		// Processar a requisição
		c.Next()

		// Verificar se há erros definidos no contexto
		if len(c.Errors) > 0 {
			err := c.Errors.Last()
			handleError(c, getStatusCode(err), err.Error(), err.Err)
			return
		}
	}
}

// handleError processa e retorna uma resposta de erro padronizada
func handleError(c *gin.Context, statusCode int, message string, err interface{}) {
	// Não escrever resposta se já foi escrita
	if c.Writer.Written() {
		return
	}

	// Criar ErrorResponse
	errorResponse := models.ErrorResponse{
		Timestamp: time.Now(),
		Status:    statusCode,
		Error:     http.StatusText(statusCode),
		Message:   message,
		Path:      c.Request.URL.Path,
	}

	// Adicionar detalhes se for um erro específico
	if details := extractErrorDetails(err); len(details) > 0 {
		errorResponse.Details = details
	}

	// Retornar resposta JSON
	c.JSON(statusCode, errorResponse)
}

// getStatusCode determina o código HTTP apropriado baseado no tipo de erro
func getStatusCode(err *gin.Error) int {
	if err == nil {
		return http.StatusInternalServerError
	}

	// Verificar se é um erro de serviço downstream
	if catalogErr, ok := err.Err.(*clients.CatalogServiceException); ok {
		// Se o serviço retornou um status code, usar ele
		if catalogErr.Status > 0 {
			// Mapear erros de serviço downstream para Bad Gateway
			if catalogErr.Status >= 500 {
				return http.StatusBadGateway
			}
			// Para 4xx, retornar o mesmo código
			return catalogErr.Status
		}
		return http.StatusBadGateway
	}

	if searchErr, ok := err.Err.(*clients.SearchServiceException); ok {
		// Se o serviço retornou um status code, usar ele
		if searchErr.Status > 0 {
			// Mapear erros de serviço downstream para Bad Gateway
			if searchErr.Status >= 500 {
				return http.StatusBadGateway
			}
			// Para 4xx, retornar o mesmo código
			return searchErr.Status
		}
		return http.StatusBadGateway
	}

	// Verificar tipo de erro do Gin
	switch err.Type {
	case gin.ErrorTypeBind:
		// Erro de binding/validação
		return http.StatusBadRequest
	case gin.ErrorTypePublic:
		// Erro público (já formatado)
		return http.StatusBadRequest
	case gin.ErrorTypePrivate:
		// Erro privado (interno)
		return http.StatusInternalServerError
	default:
		return http.StatusInternalServerError
	}
}

// extractErrorDetails extrai detalhes de validação de erros
func extractErrorDetails(err interface{}) map[string]string {
	details := make(map[string]string)

	if ginErr, ok := err.(*gin.Error); ok {
		// Se for erro de validação, tentar extrair campos
		if ginErr.Type == gin.ErrorTypeBind {
			// Detalhes de validação podem ser extraídos aqui
			details["validation_error"] = ginErr.Error()
		}
	}

	return details
}

