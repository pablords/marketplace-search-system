package handlers

import (
	"fmt"

	"github.com/gin-gonic/gin"

	"api-gateway-go/internal/clients"
)

// SearchQuery representa os parâmetros de query para busca de produtos
type SearchQuery struct {
	Query        string  `form:"query" binding:"required"`
	CategoryID   *string `form:"categoryId"`
	Page         int     `form:"page" binding:"omitempty,min=0"`
	Size         int     `form:"size" binding:"omitempty,min=1,max=100"`
	Sort         string  `form:"sort"`
	UserID       *string `form:"userId"`
	RankingDebug bool    `form:"ranking_debug"`
}

// SuggestionsQuery representa os parâmetros de query para sugestões
type SuggestionsQuery struct {
	Term  string `form:"term" binding:"required"`
	Limit int    `form:"limit" binding:"omitempty,min=1,max=20"`
}

// SearchHandler lida com requisições de busca
type SearchHandler struct {
	searchClient *clients.SearchClient
}

// NewSearchHandler cria uma nova instância do SearchHandler
func NewSearchHandler(searchClient *clients.SearchClient) *SearchHandler {
	return &SearchHandler{
		searchClient: searchClient,
	}
}

// SearchProducts busca produtos
// @Summary      Buscar produtos
// @Description  Busca produtos no marketplace usando o search-service com suporte a filtros, paginação e ordenação
// @Tags         search
// @Accept       json
// @Produce      json
// @Param        query       query     string   true   "Termo de busca"
// @Param        categoryId  query     string   false  "ID da categoria para filtrar"
// @Param        page        query     int      false  "Número da página (padrão: 0)"  default(0)
// @Param        size        query     int      false  "Tamanho da página (padrão: 20, máximo: 100)"  default(20)  maximum(100)
// @Param        sort        query     string   false  "Critério de ordenação (padrão: relevance)"  default(relevance)
// @Param        userId      query     string   false  "ID do usuário para personalização"
// @Success      200         {object}  models.SearchResult  "Resultado da busca"
// @Failure      400         {object}  models.ErrorResponse  "Requisição inválida"
// @Failure      502         {object}  models.ErrorResponse  "Erro no search-service"
// @Failure      500         {object}  models.ErrorResponse  "Erro interno do servidor"
// @Router       /search/products [get]
func (h *SearchHandler) SearchProducts(c *gin.Context) {
	// Obter query parameters validados do contexto (setado pelo middleware ValidateQuery)
	validatedDTO, exists := c.Get("validated_dto")
	if !exists {
		// Se não existe, tentar fazer bind manualmente
		var query SearchQuery
		if err := c.ShouldBindQuery(&query); err != nil {
			c.Error(err).SetType(gin.ErrorTypeBind)
			c.Abort()
			return
		}

		// Validar query obrigatória
		if query.Query == "" {
			c.Error(fmt.Errorf("query parameter is required")).SetType(gin.ErrorTypeBind)
			c.Abort()
			return
		}

		// Valores padrão
		if query.Page == 0 {
			query.Page = 0
		}
		if query.Size == 0 {
			query.Size = 20
		}
		if query.Sort == "" {
			query.Sort = "relevance"
		}

		validatedDTO = &query
	}

	query, ok := validatedDTO.(*SearchQuery)
	if !ok {
		// Tentar fazer cast de SearchQuery (sem ponteiro)
		if queryVal, ok := validatedDTO.(SearchQuery); ok {
			query = &queryVal
		} else {
			c.Error(fmt.Errorf("tipo de query inválido")).SetType(gin.ErrorTypePrivate)
			c.Abort()
			return
		}
	}

	// Aplicar valores padrão
	if query.Size == 0 {
		query.Size = 20
	}
	if query.Sort == "" {
		query.Sort = "relevance"
	}

	// Validar query obrigatória
	if query.Query == "" || len(query.Query) == 0 {
		c.Error(fmt.Errorf("search terms cannot be null or empty")).SetType(gin.ErrorTypeBind)
		c.Abort()
		return
	}

	// Criar contexto com timeout
	ctx := c.Request.Context()

	// Chamar search service
	result, err := h.searchClient.SearchProducts(
		ctx,
		query.Query,
		query.CategoryID,
		query.Page,
		query.Size,
		query.Sort,
		query.UserID,
		query.RankingDebug,
	)
	if err != nil {
		// Verificar tipo de erro
		if searchErr, ok := err.(*clients.SearchServiceException); ok {
			// Adicionar erro ao contexto para o middleware de erro processar
			c.Error(searchErr).SetType(gin.ErrorTypePublic)
			c.Abort()
			return
		}

		// Erro genérico
		c.Error(err).SetType(gin.ErrorTypePrivate)
		c.Abort()
		return
	}

	// Retornar resultado
	c.JSON(200, result)
}

// GetSuggestions obtém sugestões de busca
// @Summary      Obter sugestões de busca
// @Description  Retorna sugestões de termos de busca baseadas no termo parcial fornecido
// @Tags         search
// @Accept       json
// @Produce      json
// @Param        term   query     string  true   "Termo parcial para busca de sugestões"
// @Param        limit  query     int     false  "Número máximo de sugestões (padrão: 10, máximo: 20)"  default(10)  maximum(20)
// @Success      200    {array}   string  "Lista de sugestões"
// @Failure      400    {object}  models.ErrorResponse  "Requisição inválida"
// @Failure      502    {object}  models.ErrorResponse  "Erro no search-service"
// @Failure      500    {object}  models.ErrorResponse  "Erro interno do servidor"
// @Router       /search/suggestions [get]
func (h *SearchHandler) GetSuggestions(c *gin.Context) {
	// Obter query parameters validados do contexto (setado pelo middleware ValidateQuery)
	validatedDTO, exists := c.Get("validated_dto")
	if !exists {
		// Se não existe, tentar fazer bind manualmente
		var query SuggestionsQuery
		if err := c.ShouldBindQuery(&query); err != nil {
			c.Error(err).SetType(gin.ErrorTypeBind)
			c.Abort()
			return
		}

		// Valores padrão
		if query.Limit == 0 {
			query.Limit = 10
		}

		validatedDTO = &query
	}

	query, ok := validatedDTO.(*SuggestionsQuery)
	if !ok {
		// Tentar fazer cast de SuggestionsQuery (sem ponteiro)
		if queryVal, ok := validatedDTO.(SuggestionsQuery); ok {
			query = &queryVal
		} else {
			c.Error(fmt.Errorf("tipo de query inválido")).SetType(gin.ErrorTypePrivate)
			c.Abort()
			return
		}
	}

	// Aplicar valores padrão
	if query.Limit == 0 {
		query.Limit = 10
	}

	// Criar contexto com timeout
	ctx := c.Request.Context()

	// Chamar search service
	suggestions, err := h.searchClient.GetSuggestions(ctx, query.Term, query.Limit)
	if err != nil {
		// Para sugestões, sempre retornar lista vazia em caso de erro (comportamento do Java)
		c.JSON(200, []string{})
		return
	}

	// Retornar sugestões (ou lista vazia se nil)
	if suggestions == nil {
		suggestions = []string{}
	}

	c.JSON(200, suggestions)
}

// GetProduct busca um produto específico por ID
// @Summary      Obter produto por ID
// @Description  Busca um produto específico pelo ID através do search-service
// @Tags         search
// @Accept       json
// @Produce      json
// @Param        id   path      string  true  "ID do produto"
// @Success      200  {object}  models.Product  "Produto encontrado"
// @Failure      400  {object}  models.ErrorResponse  "Requisição inválida"
// @Failure      404  {object}  models.ErrorResponse  "Produto não encontrado"
// @Failure      502  {object}  models.ErrorResponse  "Erro no search-service"
// @Failure      500  {object}  models.ErrorResponse  "Erro interno do servidor"
// @Router       /search/products/{id} [get]
func (h *SearchHandler) GetProduct(c *gin.Context) {
	productID := c.Param("id")
	if productID == "" {
		c.Error(fmt.Errorf("product ID is required")).SetType(gin.ErrorTypeBind)
		c.Abort()
		return
	}

	// Criar contexto com timeout
	ctx := c.Request.Context()

	// Chamar search service
	product, err := h.searchClient.GetProduct(ctx, productID)
	if err != nil {
		// Verificar tipo de erro
		if searchErr, ok := err.(*clients.SearchServiceException); ok {
			// Se for 404, retornar 404
			if searchErr.Status == 404 {
				c.Error(searchErr).SetType(gin.ErrorTypePublic)
				c.Abort()
				return
			}

			// Outros erros
			c.Error(searchErr).SetType(gin.ErrorTypePublic)
			c.Abort()
			return
		}

		// Erro genérico
		c.Error(err).SetType(gin.ErrorTypePrivate)
		c.Abort()
		return
	}

	// Retornar produto
	c.JSON(200, product)
}

