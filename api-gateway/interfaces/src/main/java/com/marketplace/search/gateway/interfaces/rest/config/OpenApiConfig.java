package com.marketplace.search.gateway.interfaces.rest.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${server.servlet.context-path:/}")
    private String contextPath;

    @Value("${spring.application.name:api-gateway}")
    private String applicationName;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Marketplace API Gateway")
                        .version("1.0.0")
                        .description("""
                                API Gateway para o sistema de marketplace.
                                
                                Este gateway roteia requisições para os seguintes microserviços:
                                - Catalog Service: Gerenciamento de produtos
                                - Search Service: Busca de produtos
                                
                                Funcionalidades:
                                - Roteamento de requisições
                                - Circuit breaker e retry
                                - Health checks
                                - Monitoramento e observabilidade
                                """)
                        .contact(new Contact()
                                .name("Marketplace API Gateway Team")
                                .email("gateway-team@marketplace.com")
                                .url("https://marketplace.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort + contextPath)
                                .description("Servidor de desenvolvimento"),
                        new Server()
                                .url("https://api-staging.marketplace.com" + contextPath)
                                .description("Servidor de staging"),
                        new Server()
                                .url("https://api.marketplace.com" + contextPath)
                                .description("Servidor de produção")));
    }
}

