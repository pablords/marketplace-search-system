package middleware

import (
	"net/http"
	"reflect"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/go-playground/validator/v10"

	"api-gateway-go/internal/models"
)

var validate *validator.Validate

func init() {
	validate = validator.New()
}

// ValidateJSON valida um DTO JSON usando go-playground/validator
// Uso: router.POST("/products", ValidateJSON(&models.Product{}), handler)
func ValidateJSON(dtoTemplate interface{}) gin.HandlerFunc {
	dtoType := reflect.TypeOf(dtoTemplate)
	if dtoType.Kind() == reflect.Ptr {
		dtoType = dtoType.Elem()
	}

	return func(c *gin.Context) {
		dto := reflect.New(dtoType).Interface()

		// Bind JSON para o DTO
		if err := c.ShouldBindJSON(dto); err != nil {
			// Erro de binding (JSON inválido, etc.)
			c.Error(err).SetType(gin.ErrorTypeBind)
			c.AbortWithStatusJSON(http.StatusBadRequest, createValidationErrorResponse(c, err))
			return
		}

		// Validar struct usando validator
		if err := validate.Struct(dto); err != nil {
			validationErrors := extractValidationErrors(err)
			c.Error(err).SetType(gin.ErrorTypeBind)
			c.AbortWithStatusJSON(http.StatusBadRequest, createValidationErrorResponseWithDetails(c, validationErrors))
			return
		}

		// Armazenar DTO validado no contexto para uso no handler
		c.Set("validated_dto", dto)
		c.Next()
	}
}

// ValidateQuery valida query parameters
// Uso: router.GET("/search", ValidateQuery(&SearchQuery{}), handler)
func ValidateQuery(dtoTemplate interface{}) gin.HandlerFunc {
	dtoType := reflect.TypeOf(dtoTemplate)
	if dtoType.Kind() == reflect.Ptr {
		dtoType = dtoType.Elem()
	}

	return func(c *gin.Context) {
		dto := reflect.New(dtoType).Interface()

		// Bind query parameters para o DTO
		if err := c.ShouldBindQuery(dto); err != nil {
			c.Error(err).SetType(gin.ErrorTypeBind)
			c.AbortWithStatusJSON(http.StatusBadRequest, createValidationErrorResponse(c, err))
			return
		}

		// Validar struct usando validator
		if err := validate.Struct(dto); err != nil {
			validationErrors := extractValidationErrors(err)
			c.Error(err).SetType(gin.ErrorTypeBind)
			c.AbortWithStatusJSON(http.StatusBadRequest, createValidationErrorResponseWithDetails(c, validationErrors))
			return
		}

		// Armazenar DTO validado no contexto
		c.Set("validated_dto", dto)
		c.Next()
	}
}

// ValidateURI valida URI parameters
// Uso: router.GET("/products/:id", ValidateURI(&ProductID{}), handler)
func ValidateURI(dtoTemplate interface{}) gin.HandlerFunc {
	dtoType := reflect.TypeOf(dtoTemplate)
	if dtoType.Kind() == reflect.Ptr {
		dtoType = dtoType.Elem()
	}

	return func(c *gin.Context) {
		dto := reflect.New(dtoType).Interface()

		// Bind URI parameters para o DTO
		if err := c.ShouldBindUri(dto); err != nil {
			c.Error(err).SetType(gin.ErrorTypeBind)
			c.AbortWithStatusJSON(http.StatusBadRequest, createValidationErrorResponse(c, err))
			return
		}

		// Validar struct usando validator
		if err := validate.Struct(dto); err != nil {
			validationErrors := extractValidationErrors(err)
			c.Error(err).SetType(gin.ErrorTypeBind)
			c.AbortWithStatusJSON(http.StatusBadRequest, createValidationErrorResponseWithDetails(c, validationErrors))
			return
		}

		// Armazenar DTO validado no contexto
		c.Set("validated_dto", dto)
		c.Next()
	}
}

// extractValidationErrors extrai erros de validação em um formato legível
func extractValidationErrors(err error) map[string]string {
	errors := make(map[string]string)

	if validationErrors, ok := err.(validator.ValidationErrors); ok {
		for _, fieldError := range validationErrors {
			field := fieldError.Field()
			tag := fieldError.Tag()
			
			// Mensagem de erro customizada baseada na tag
			var message string
			switch tag {
			case "required":
				message = "Campo obrigatório"
			case "email":
				message = "Email inválido"
			case "min":
				message = "Valor abaixo do mínimo permitido"
			case "max":
				message = "Valor acima do máximo permitido"
			case "gt":
				message = "Valor deve ser maior que " + fieldError.Param()
			case "gte":
				message = "Valor deve ser maior ou igual a " + fieldError.Param()
			case "lt":
				message = "Valor deve ser menor que " + fieldError.Param()
			case "lte":
				message = "Valor deve ser menor ou igual a " + fieldError.Param()
			case "oneof":
				message = "Valor deve ser um dos: " + fieldError.Param()
			default:
				message = "Valor inválido para o campo " + field
			}

			errors[field] = message
		}
	}

	return errors
}

// createValidationErrorResponse cria uma resposta de erro de validação
func createValidationErrorResponse(c *gin.Context, err error) models.ErrorResponse {
	return models.ErrorResponse{
		Timestamp: time.Now(),
		Status:    http.StatusBadRequest,
		Error:     "Bad Request",
		Message:   "Erro de validação na requisição",
		Path:      c.Request.URL.Path,
		Details: map[string]string{
			"error": err.Error(),
		},
	}
}

// createValidationErrorResponseWithDetails cria uma resposta de erro de validação com detalhes
func createValidationErrorResponseWithDetails(c *gin.Context, details map[string]string) models.ErrorResponse {
	return models.ErrorResponse{
		Timestamp: time.Now(),
		Status:    http.StatusBadRequest,
		Error:     "Bad Request",
		Message:   "Erro de validação na requisição",
		Path:      c.Request.URL.Path,
		Details:   details,
	}
}

