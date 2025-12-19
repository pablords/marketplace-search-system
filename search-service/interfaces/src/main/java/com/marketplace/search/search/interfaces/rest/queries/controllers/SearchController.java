package com.marketplace.search.search.interfaces.rest.queries.controllers;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marketplace.search.search.application.queries.SearchRequestQuery;
import com.marketplace.search.search.application.queries.SearchResultQuery;
import com.marketplace.search.search.application.usecases.SearchProductsUseCase;
import com.marketplace.search.search.application.mappers.SearchMapper;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Controller REST para operações de busca de produtos
 */
@RestController
@RequestMapping("/search")
@Validated
public class SearchController {

	private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

	private final SearchProductsUseCase searchProductsUseCase;
	private final SearchMapper searchMapper;

	public SearchController(SearchProductsUseCase searchProductsUseCase, SearchMapper searchMapper) {
		this.searchProductsUseCase = searchProductsUseCase;
		this.searchMapper = searchMapper;
	}

	/**
	 * Endpoint para buscar produtos
	 * 
	 * @param query Termo de busca
	 * @param categoryId ID da categoria (opcional)
	 * @param page Número da página (padrão: 0)
	 * @param size Tamanho da página (padrão: 20, máximo: 100)
	 * @param sort Campo de ordenação (padrão: RELEVANCE)
	 * @param userId ID do usuário para personalização (opcional)
	 * @return Resultado da busca
	 */
	@GetMapping("/products")
	public CompletableFuture<ResponseEntity<SearchResultQuery>> searchProducts(
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String categoryId,
			@RequestParam(defaultValue = "0") @Min(0) Integer page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size,
			@RequestParam(defaultValue = "RELEVANCE") String sort,
			@RequestParam(required = false) String userId) {

		logger.info("Requisição de busca recebida: query={}, categoryId={}, page={}, size={}", query, categoryId, page,
				size);

		if (query == null || query.trim().isEmpty()) {
			throw new IllegalArgumentException("Termo de busca não pode ser nulo ou vazio");
		}

		// Construir SearchRequestQuery
		SearchRequestQuery searchRequest = SearchRequestQuery.builder().query(query.trim())
				.categoryId(categoryId).offset(page * size).limit(size).sort(sort).build();

		// Executar busca de forma assíncrona
		CompletableFuture<SearchResultQuery> searchResult = searchProductsUseCase.executeAsync(searchRequest)
				.thenApply(result -> {
					logger.info("Busca concluída: totalResults={}", result.totalCount());
					return result;
				});

		SearchResultQuery resultDTO = searchResult.join();

		return CompletableFuture.completedFuture(ResponseEntity.ok(resultDTO));
	}

	/**
	 * Endpoint para obter sugestões de busca
	 * 
	 * @param term Termo parcial para sugestões
	 * @param limit Limite de sugestões (padrão: 10, máximo: 20)
	 * @return Lista de sugestões
	 */
	@GetMapping("/suggestions")
	public ResponseEntity<List<String>> getSuggestions(@RequestParam @NotBlank String term,
			@RequestParam(defaultValue = "10") @Min(1) @Max(20) Integer limit) {

		logger.info("Requisição de sugestões recebida: term={}, limit={}", term, limit);

		// TODO: Implementar busca de sugestões usando o repositório
		// Por enquanto, retornar lista vazia
		return ResponseEntity.ok(List.of());
	}

	/**
	 * Endpoint para buscar um produto específico por ID
	 * 
	 * @param productId ID do produto
	 * @return Dados do produto
	 */
	@GetMapping("/products/{productId}")
	public ResponseEntity<com.marketplace.search.search.application.queries.ProductData> getProduct(
			@RequestParam @NotBlank String productId) {

		logger.info("Requisição de produto por ID: productId={}", productId);

		// TODO: Implementar busca de produto por ID usando o repositório
		// Por enquanto, retornar 404
		return ResponseEntity.notFound().build();
	}
}

