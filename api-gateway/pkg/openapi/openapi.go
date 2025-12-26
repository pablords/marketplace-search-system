package openapi

import (
	"github.com/gin-gonic/gin"
	swaggerFiles "github.com/swaggo/files"
	ginSwagger "github.com/swaggo/gin-swagger"
)

// SetupSwagger configura os endpoints do Swagger UI e OpenAPI docs
// Nota: Requer que a documentação tenha sido gerada com 'swag init'
// e que o pacote 'docs' seja importado no main.go
//
// Parâmetros:
//   - router: Router do Gin (raiz, não o grupo com context path)
//   - docsPath: Path para o endpoint de documentação JSON (ex: "/api-docs")
//   - swaggerUIPath: Path para o Swagger UI (ex: "/swagger-ui.html")
func SetupSwagger(router *gin.Engine, docsPath, swaggerUIPath string) {
	// Swagger UI - serve a interface web do Swagger
	// O gin-swagger expõe automaticamente em /swagger/index.html
	// e /swagger/doc.json para o JSON da documentação
	router.GET("/swagger/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))

	// Endpoint customizado para o JSON do OpenAPI (se especificado)
	if docsPath != "" {
		// Normalizar path (remover leading slash se existir)
		if len(docsPath) > 0 && docsPath[0] == '/' {
			docsPath = docsPath[1:]
		}
		// Criar endpoint que redireciona para o JSON gerado pelo swag
		router.GET(docsPath, func(c *gin.Context) {
			c.Redirect(302, "/swagger/doc.json")
		})
	}

	// Endpoint customizado para o Swagger UI (se especificado e diferente do padrão)
	if swaggerUIPath != "" {
		// Normalizar path (remover leading slash se existir)
		if len(swaggerUIPath) > 0 && swaggerUIPath[0] == '/' {
			swaggerUIPath = swaggerUIPath[1:]
		}
		// Se for diferente de "swagger", criar rota adicional
		if swaggerUIPath != "swagger" && swaggerUIPath != "swagger/*any" {
			router.GET(swaggerUIPath+"/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))
		}
	}
}
