package handlers

import (
	"fmt"
	"net/http"
	"net/url"

	"github.com/gin-gonic/gin"

	"api-gateway-go/internal/clients"
	"api-gateway-go/internal/models"
)

// ProductHandler lida com requisições relacionadas a produtos
type ProductHandler struct {
	catalogClient *clients.CatalogClient
}

// NewProductHandler cria uma nova instância do ProductHandler
func NewProductHandler(catalogClient *clients.CatalogClient) *ProductHandler {
	return &ProductHandler{
		catalogClient: catalogClient,
	}
}

// CreateProduct cria um novo produto
// @Summary      Criar produto
// @Description  Cria um novo produto no catálogo através do catalog-service
// @Tags         products
// @Accept       json
// @Produce      json
// @Param        product  body      models.Product  true  "Dados do produto a ser criado"
// @Success      201      {string}  string          "Produto criado com sucesso"
// @Failure      400      {object}  models.ErrorResponse  "Requisição inválida"
// @Failure      502      {object}  models.ErrorResponse  "Erro no catalog-service"
// @Failure      500      {object}  models.ErrorResponse  "Erro interno do servidor"
// @Router       /products [post]
func (h *ProductHandler) CreateProduct(c *gin.Context) {
	// Obter o produto validado do contexto (setado pelo middleware ValidateJSON)
	validatedDTO, exists := c.Get("validated_dto")
	if !exists {
		// Se não existe, tentar fazer bind manualmente
		var product models.Product
		if err := c.ShouldBindJSON(&product); err != nil {
			c.Error(err).SetType(gin.ErrorTypeBind)
			c.Abort()
			return
		}
		validatedDTO = &product
	}

	product, ok := validatedDTO.(*models.Product)
	if !ok {
		// Tentar fazer cast de models.Product (sem ponteiro)
		if productVal, ok := validatedDTO.(models.Product); ok {
			product = &productVal
		} else {
			c.Error(fmt.Errorf("tipo de DTO inválido")).SetType(gin.ErrorTypePrivate)
			c.Abort()
			return
		}
	}

	// Criar contexto com timeout
	ctx := c.Request.Context()

	// Chamar catalog service
	resp, err := h.catalogClient.CreateProduct(ctx, product)
	if err != nil {
		// Verificar tipo de erro
		if catalogErr, ok := err.(*clients.CatalogServiceException); ok {
			// Adicionar erro ao contexto para o middleware de erro processar
			c.Error(catalogErr).SetType(gin.ErrorTypePublic)
			c.Abort()
			return
		}

		// Erro genérico
		c.Error(err).SetType(gin.ErrorTypePrivate)
		c.Abort()
		return
	}
	defer resp.Body.Close()

	// Extrair header Location da resposta
	location := resp.Header.Get("Location")
	if location == "" {
		// Fallback: construir URI baseado no ID se não retornado pelo serviço
		location = fmt.Sprintf("/api/v1/products/%s", url.PathEscape(product.ID))
	}

	// Retornar 201 Created com header Location
	c.Header("Location", location)
	c.Status(http.StatusCreated)
}

