package com.marketplace.search.infrastructure.clients;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.marketplace.search.interfaces.rest.dtos.SearchResultDTO;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Cliente HTTP para comunicação com o search-service.
 * Usa WebClient (Spring WebFlux) para chamadas assíncronas e não-bloqueantes.
 */
@Component
public class SearchServiceClient {

	private static final Logger logger = LoggerFactory.getLogger(SearchServiceClient.class);

	private final WebClient webClient;

	public SearchServiceClient(
			@org.springframework.beans.factory.annotation.Qualifier("searchServiceWebClient") WebClient searchServiceWebClient) {
		this.webClient = searchServiceWebClient;
	}

	/**
	 * Busca produtos no search-service.
	 * 
	 * @param query Termo de busca
	 * @param categoryId ID da categoria (opcional)
	 * @param page Número da página (padrão: 0)
	 * @param size Tamanho da página (padrão: 20)
	 * @param sort Campo de ordenação (padrão: RELEVANCE)
	 * @param userId ID do usuário para personalização (opcional)
	 * @return Mono com SearchResultDTO contendo os resultados da busca
	 * @throws SearchServiceException se houver erro na comunicação
	 */
	public Mono<SearchResultDTO> searchProducts(String query, String categoryId, Integer page, Integer size,
			String sort, String userId) {
		logger.debug("Enviando requisição para buscar produtos no search-service: query={}", query);

		return webClient.get()
				.uri(uriBuilder -> {
					uriBuilder.path("/search/products").queryParam("query", query);
					if (categoryId != null) {
						uriBuilder.queryParam("categoryId", categoryId);
					}
					if (page != null) {
						uriBuilder.queryParam("page", page);
					}
					if (size != null) {
						uriBuilder.queryParam("size", size);
					}
					if (sort != null) {
						uriBuilder.queryParam("sort", sort);
					}
					if (userId != null) {
						uriBuilder.queryParam("userId", userId);
					}
					return uriBuilder.build();
				})
				.retrieve()
				.bodyToMono(SearchResultDTO.class)
				.retryWhen(Retry.backoff(3, java.time.Duration.ofMillis(500))
						.filter(throwable -> throwable instanceof WebClientResponseException
								&& ((WebClientResponseException) throwable).getStatusCode().is5xxServerError())
						.doBeforeRetry(retrySignal -> logger.warn(
								"Tentando novamente buscar produtos após erro: {}", retrySignal.totalRetries())))
				.doOnSuccess(result -> logger.debug("Busca realizada com sucesso no search-service: totalResults={}",
						result.totalCount()))
				.onErrorMap(WebClientResponseException.class, ex -> {
					logger.error("Erro ao buscar produtos no search-service. Status: {}, Body: {}",
							ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
					return new SearchServiceException(
							"Erro ao buscar produtos no search-service: " + ex.getMessage(), ex);
				})
				.onErrorMap(throwable -> {
					if (throwable instanceof SearchServiceException) {
						return throwable;
					}
					logger.error("Erro inesperado ao buscar produtos no search-service", throwable);
					return new SearchServiceException(
							"Erro inesperado ao buscar produtos no search-service: " + throwable.getMessage(),
							throwable);
				});
	}

	/**
	 * Obtém sugestões de busca do search-service.
	 * 
	 * @param term Termo parcial para sugestões
	 * @param limit Limite de sugestões (padrão: 10)
	 * @return Mono com lista de sugestões
	 * @throws SearchServiceException se houver erro na comunicação
	 */
	public Mono<List<String>> getSuggestions(String term, Integer limit) {
		logger.debug("Enviando requisição para obter sugestões no search-service: term={}", term);

		return webClient.get()
				.uri(uriBuilder -> {
					uriBuilder.path("/search/suggestions").queryParam("term", term);
					if (limit != null) {
						uriBuilder.queryParam("limit", limit);
					}
					return uriBuilder.build();
				})
				.retrieve()
				.bodyToFlux(String.class)
				.collectList()
				.retryWhen(Retry.backoff(3, java.time.Duration.ofMillis(500))
						.filter(throwable -> throwable instanceof WebClientResponseException
								&& ((WebClientResponseException) throwable).getStatusCode().is5xxServerError())
						.doBeforeRetry(retrySignal -> logger.warn(
								"Tentando novamente obter sugestões após erro: {}", retrySignal.totalRetries())))
				.doOnSuccess(suggestions -> logger.debug("Sugestões obtidas com sucesso: count={}",
						suggestions.size()))
				.onErrorMap(WebClientResponseException.class, ex -> {
					logger.error("Erro ao obter sugestões no search-service. Status: {}, Body: {}",
							ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
					return new SearchServiceException(
							"Erro ao obter sugestões no search-service: " + ex.getMessage(), ex);
				})
				.onErrorReturn(Collections.emptyList())
				.onErrorMap(throwable -> {
					if (throwable instanceof SearchServiceException) {
						return throwable;
					}
					logger.error("Erro inesperado ao obter sugestões no search-service", throwable);
					return new SearchServiceException(
							"Erro inesperado ao obter sugestões no search-service: " + throwable.getMessage(),
							throwable);
				});
	}

	/**
	 * Busca um produto específico por ID no search-service.
	 * 
	 * @param productId ID do produto
	 * @return Mono com ProductDTO do produto encontrado
	 * @throws SearchServiceException se houver erro na comunicação ou produto não encontrado
	 */
	public Mono<com.marketplace.search.interfaces.rest.dtos.ProductDTO> getProduct(String productId) {
		logger.debug("Enviando requisição para buscar produto no search-service: productId={}", productId);

		return webClient.get()
				.uri("/search/products/{id}", productId)
				.retrieve()
				.bodyToMono(com.marketplace.search.interfaces.rest.dtos.ProductDTO.class)
				.retryWhen(Retry.backoff(3, java.time.Duration.ofMillis(500))
						.filter(throwable -> throwable instanceof WebClientResponseException
								&& ((WebClientResponseException) throwable).getStatusCode().is5xxServerError())
						.doBeforeRetry(retrySignal -> logger.warn(
								"Tentando novamente buscar produto após erro: {}", retrySignal.totalRetries())))
				.doOnSuccess(product -> logger.debug("Produto encontrado no search-service: {}", productId))
				.onErrorMap(WebClientResponseException.class, ex -> {
					if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
						logger.warn("Produto não encontrado no search-service: {}", productId);
						return new SearchServiceException("Produto não encontrado: " + productId, ex);
					}
					logger.error("Erro ao buscar produto no search-service. Status: {}, Body: {}",
							ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
					return new SearchServiceException(
							"Erro ao buscar produto no search-service: " + ex.getMessage(), ex);
				})
				.onErrorMap(throwable -> {
					if (throwable instanceof SearchServiceException) {
						return throwable;
					}
					logger.error("Erro inesperado ao buscar produto no search-service", throwable);
					return new SearchServiceException(
							"Erro inesperado ao buscar produto no search-service: " + throwable.getMessage(),
							throwable);
				});
	}

	/**
	 * Classe de exceção para erros do search-service
	 */
	public static class SearchServiceException extends RuntimeException {
		public SearchServiceException(String message) {
			super(message);
		}

		public SearchServiceException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}

