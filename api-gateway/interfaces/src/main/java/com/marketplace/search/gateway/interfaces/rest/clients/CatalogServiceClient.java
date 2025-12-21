package com.marketplace.search.gateway.interfaces.rest.clients;

import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.search.gateway.interfaces.rest.dtos.ProductDTO;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Cliente HTTP para comunicação com o catalog-service.
 * Usa WebClient (Spring WebFlux) para chamadas assíncronas e não-bloqueantes.
 * Implementa CatalogServicePort para evitar dependências circulares.
 */
@Component
public class CatalogServiceClient implements CatalogServicePort {

  private static final Logger logger = LoggerFactory.getLogger(CatalogServiceClient.class);

  private final WebClient webClient;
  private final ObjectMapper objectMapper;

  public CatalogServiceClient(
      @org.springframework.beans.factory.annotation.Qualifier("catalogServiceWebClient") WebClient catalogServiceWebClient,
      ObjectMapper objectMapper) {
    this.webClient = catalogServiceWebClient;
    this.objectMapper = objectMapper;
  }

  /**
   * Implementação da interface CatalogServicePort.
   * Cria um novo produto no catalog-service.
   * 
   * @param productObject Objeto do produto (deve ser ProductDTO)
   * @return URI do produto criado
   * @throws CatalogServiceException se houver erro na comunicação
   */
  @Override
  public URI createProduct(Object productObject) {
    if (!(productObject instanceof ProductDTO)) {
      throw new CatalogServiceException("Objeto deve ser do tipo ProductDTO");
    }
    ProductDTO productDTO = (ProductDTO) productObject;
    return createProductAsync(productDTO).block();
  }

  /**
   * Cria um novo produto no catalog-service (método assíncrono).
   * 
   * @param productDTO DTO do produto a ser criado
   * @return Mono com URI do produto criado
   * @throws CatalogServiceException se houver erro na comunicação
   */
  public Mono<URI> createProductAsync(ProductDTO productDTO) {
    logger.debug("Enviando requisição para criar produto no catalog-service: {}", productDTO.id());

    return webClient.post()
        .uri("/products")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(productDTO)
        .retrieve()
        .toBodilessEntity()
        .map(response -> {
          URI location = response.getHeaders().getLocation();
          if (location == null) {
            // Se não houver header Location, construir a URI baseado no ID
            location = URI.create("/products/" + productDTO.id());
          }
          logger.info("Produto criado com sucesso no catalog-service: {}", productDTO.id());
          return location;
        })
        .retryWhen(Retry.backoff(3, java.time.Duration.ofMillis(500))
            .filter(throwable -> throwable instanceof WebClientResponseException
                && ((WebClientResponseException) throwable).getStatusCode().is5xxServerError())
            .doBeforeRetry(retrySignal -> logger.warn(
                "Tentando novamente criar produto após erro: {}", retrySignal.totalRetries())))
        .onErrorMap(WebClientResponseException.class, ex -> {
          String responseBody = ex.getResponseBodyAsString();
          String errorMessage = "Erro ao criar produto no catalog-service";
          
          // Tenta extrair a mensagem de erro do corpo da resposta JSON
          if (responseBody != null && !responseBody.isEmpty()) {
            try {
              // Tenta parsear o JSON para extrair a mensagem
              Map<String, Object> errorJson = objectMapper.readValue(
                  responseBody, 
                  new TypeReference<Map<String, Object>>() {});
              if (errorJson.containsKey("message")) {
                Object messageObj = errorJson.get("message");
                if (messageObj != null) {
                  errorMessage = messageObj.toString();
                }
              } else if (errorJson.containsKey("error")) {
                Object errorObj = errorJson.get("error");
                if (errorObj != null) {
                  errorMessage = errorObj.toString();
                }
              }
            } catch (Exception e) {
              // Se não conseguir parsear JSON, usa o corpo completo (limitado)
              logger.debug("Não foi possível parsear JSON do corpo da resposta", e);
              if (responseBody.length() > 200) {
                errorMessage = responseBody.substring(0, 200) + "...";
              } else {
                errorMessage = responseBody;
              }
            }
          }
          
          logger.error("Erro ao criar produto no catalog-service. Status: {}, Mensagem: {}",
              ex.getStatusCode(), errorMessage, ex);
          return new CatalogServiceException(errorMessage, ex);
        })
        .onErrorMap(throwable -> {
          if (throwable instanceof CatalogServiceException) {
            return throwable;
          }
          logger.error("Erro inesperado ao criar produto no catalog-service", throwable);
          return new CatalogServiceException(
              "Erro inesperado ao criar produto no catalog-service: " + throwable.getMessage(),
              throwable);
        });
  }

}

