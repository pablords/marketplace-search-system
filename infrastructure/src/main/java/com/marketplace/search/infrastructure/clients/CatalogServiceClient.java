package com.marketplace.search.infrastructure.clients;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.marketplace.search.application.clients.CatalogServicePort;
import com.marketplace.search.interfaces.rest.dtos.ProductDTO;

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

  public CatalogServiceClient(@org.springframework.beans.factory.annotation.Qualifier("catalogServiceWebClient") WebClient catalogServiceWebClient) {
    this.webClient = catalogServiceWebClient;
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
          logger.error("Erro ao criar produto no catalog-service. Status: {}, Body: {}",
              ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
          return new CatalogServiceException(
              "Erro ao criar produto no catalog-service: " + ex.getMessage(), ex);
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

  /**
   * Classe de exceção interna (compatível com a interface).
   */
  public static class CatalogServiceException extends CatalogServicePort.CatalogServiceException {
    public CatalogServiceException(String message) {
      super(message);
    }

    public CatalogServiceException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Atualiza um produto existente no catalog-service.
   * 
   * @param productId ID do produto a ser atualizado
   * @param productDTO DTO com os dados atualizados do produto
   * @return Mono vazio que completa quando a atualização for bem-sucedida
   * @throws CatalogServiceException se houver erro na comunicação
   */
  public Mono<Void> updateProduct(String productId, ProductDTO productDTO) {
    logger.debug("Enviando requisição para atualizar produto no catalog-service: {}", productId);

    return webClient.put()
        .uri("/products/{id}", productId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(productDTO)
        .retrieve()
        .toBodilessEntity()
        .then()
        .retryWhen(Retry.backoff(3, java.time.Duration.ofMillis(500))
            .filter(throwable -> throwable instanceof WebClientResponseException
                && ((WebClientResponseException) throwable).getStatusCode().is5xxServerError())
            .doBeforeRetry(retrySignal -> logger.warn(
                "Tentando novamente atualizar produto após erro: {}", retrySignal.totalRetries())))
        .doOnSuccess(v -> logger.info("Produto atualizado com sucesso no catalog-service: {}", productId))
        .onErrorMap(WebClientResponseException.class, ex -> {
          if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
            logger.warn("Produto não encontrado no catalog-service: {}", productId);
            return new CatalogServiceException("Produto não encontrado: " + productId, ex);
          }
          logger.error("Erro ao atualizar produto no catalog-service. Status: {}, Body: {}",
              ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
          return new CatalogServiceException(
              "Erro ao atualizar produto no catalog-service: " + ex.getMessage(), ex);
        })
        .onErrorMap(throwable -> {
          if (throwable instanceof CatalogServiceException) {
            return throwable;
          }
          logger.error("Erro inesperado ao atualizar produto no catalog-service", throwable);
          return new CatalogServiceException(
              "Erro inesperado ao atualizar produto no catalog-service: " + throwable.getMessage(),
              throwable);
        });
  }

  /**
   * Deleta um produto do catalog-service.
   * 
   * @param productId ID do produto a ser deletado
   * @return Mono vazio que completa quando a deleção for bem-sucedida
   * @throws CatalogServiceException se houver erro na comunicação
   */
  public Mono<Void> deleteProduct(String productId) {
    logger.debug("Enviando requisição para deletar produto no catalog-service: {}", productId);

    return webClient.delete()
        .uri("/products/{id}", productId)
        .retrieve()
        .toBodilessEntity()
        .then()
        .retryWhen(Retry.backoff(3, java.time.Duration.ofMillis(500))
            .filter(throwable -> throwable instanceof WebClientResponseException
                && ((WebClientResponseException) throwable).getStatusCode().is5xxServerError())
            .doBeforeRetry(retrySignal -> logger.warn(
                "Tentando novamente deletar produto após erro: {}", retrySignal.totalRetries())))
        .doOnSuccess(v -> logger.info("Produto deletado com sucesso no catalog-service: {}", productId))
        .onErrorMap(WebClientResponseException.class, ex -> {
          if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
            logger.warn("Produto não encontrado no catalog-service: {}", productId);
            return new CatalogServiceException("Produto não encontrado: " + productId, ex);
          }
          logger.error("Erro ao deletar produto no catalog-service. Status: {}, Body: {}",
              ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
          return new CatalogServiceException(
              "Erro ao deletar produto no catalog-service: " + ex.getMessage(), ex);
        })
        .onErrorMap(throwable -> {
          if (throwable instanceof CatalogServiceException) {
            return throwable;
          }
          logger.error("Erro inesperado ao deletar produto no catalog-service", throwable);
          return new CatalogServiceException(
              "Erro inesperado ao deletar produto no catalog-service: " + throwable.getMessage(),
              throwable);
        });
  }

  /**
   * Busca um produto por ID no catalog-service.
   * 
   * @param productId ID do produto
   * @return Mono com o ProductDTO do produto encontrado
   * @throws CatalogServiceException se houver erro na comunicação ou produto não encontrado
   */
  public Mono<ProductDTO> getProduct(String productId) {
    logger.debug("Enviando requisição para buscar produto no catalog-service: {}", productId);

    return webClient.get()
        .uri("/products/{id}", productId)
        .retrieve()
        .bodyToMono(ProductDTO.class)
        .retryWhen(Retry.backoff(3, java.time.Duration.ofMillis(500))
            .filter(throwable -> throwable instanceof WebClientResponseException
                && ((WebClientResponseException) throwable).getStatusCode().is5xxServerError())
            .doBeforeRetry(retrySignal -> logger.warn(
                "Tentando novamente buscar produto após erro: {}", retrySignal.totalRetries())))
        .doOnSuccess(product -> logger.debug("Produto encontrado no catalog-service: {}", productId))
        .onErrorMap(WebClientResponseException.class, ex -> {
          if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
            logger.warn("Produto não encontrado no catalog-service: {}", productId);
            return new CatalogServiceException("Produto não encontrado: " + productId, ex);
          }
          logger.error("Erro ao buscar produto no catalog-service. Status: {}, Body: {}",
              ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
          return new CatalogServiceException(
              "Erro ao buscar produto no catalog-service: " + ex.getMessage(), ex);
        })
        .onErrorMap(throwable -> {
          if (throwable instanceof CatalogServiceException) {
            return throwable;
          }
          logger.error("Erro inesperado ao buscar produto no catalog-service", throwable);
          return new CatalogServiceException(
              "Erro inesperado ao buscar produto no catalog-service: " + throwable.getMessage(),
              throwable);
        });
  }

}

