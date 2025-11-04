package com.marketplace.search.interfaces.rest.queries.controllers;

import java.util.Collections;
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

import com.marketplace.search.application.queries.SearchRequestQuery;
import com.marketplace.search.application.queries.SearchResultQuery;
import com.marketplace.search.application.usecases.SearchProductsUseCase;
import com.marketplace.search.interfaces.rest.dtos.SearchRequestDTO;
import com.marketplace.search.interfaces.rest.dtos.SearchResultDTO;
import com.marketplace.search.interfaces.rest.queries.mappers.SearchMapper;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/search")
@Validated
public class SearchQueryController implements SearchApiDoc {

  private final SearchProductsUseCase searchProductsUseCase;

  private final SearchMapper searchMapper;
  private static final Logger logger = LoggerFactory.getLogger(SearchQueryController.class);

  public SearchQueryController(
      SearchProductsUseCase searchProductsUseCase,
      SearchMapper searchMapper) {
    this.searchProductsUseCase = searchProductsUseCase;
    this.searchMapper = searchMapper;
  }

  @GetMapping("/products")
  public CompletableFuture<ResponseEntity<SearchResultDTO>> searchProducts(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String categoryId,
      @RequestParam(required = false) String brand,
      @RequestParam(required = false) Double minPrice,
      @RequestParam(required = false) Double maxPrice,
      @RequestParam(required = false) String condition,
      @RequestParam(required = false) String sellerId,
      @RequestParam(defaultValue = "0") @Min(0) Integer page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size,
      @RequestParam(defaultValue = "relevance") String sortBy,
      @RequestParam(defaultValue = "desc") String sortDirection,
      @RequestParam(required = false) String userId) {

    logger.info("Received search request: query={}", query);

    if (query == null || query.trim().isEmpty()) {
      throw new IllegalArgumentException("Search terms cannot be null or empty");
    }

    SearchRequestDTO searchRequest = SearchRequestDTO.builder()
        .query(query.trim())
        .build();

    SearchRequestQuery searchQuery = searchMapper.toqQuery(searchRequest);

    CompletableFuture<SearchResultQuery> searchResult = searchProductsUseCase.executeAsync(searchQuery)
        .thenApply(result -> {
          logger.info("Search completed: totalResults={}", result.totalCount());
          return result;
        });

    SearchResultDTO resultDTO = searchMapper.toDto(searchResult.join());

    return CompletableFuture.completedFuture(ResponseEntity.ok(resultDTO));
  }


  @GetMapping("/suggestions")
  public ResponseEntity<List<String>> getSuggestions(
      @RequestParam @NotBlank String term,
      @RequestParam(defaultValue = "10") @Min(1) @Max(20) Integer limit) {

    // Implementação das sugestões será adicionada posteriormente
    return ResponseEntity.ok(Collections.emptyList());
  }

}