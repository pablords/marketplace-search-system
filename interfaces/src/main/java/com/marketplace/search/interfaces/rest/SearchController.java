package com.marketplace.search.interfaces.rest;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marketplace.search.application.dto.ProductDTO;
import com.marketplace.search.application.dto.SearchRequestDTO;
import com.marketplace.search.application.dto.SearchResultDTO;
import com.marketplace.search.application.usecases.IndexProductUseCase;
import com.marketplace.search.application.usecases.SearchProductsUseCase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/search")
@Validated
public class SearchController implements SearchApi {

  private final SearchProductsUseCase searchProductsUseCase;
  private final IndexProductUseCase indexProductUseCase;
  private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

  public SearchController(
      SearchProductsUseCase searchProductsUseCase,
      IndexProductUseCase indexProductUseCase) {
    this.searchProductsUseCase = searchProductsUseCase;
    this.indexProductUseCase = indexProductUseCase;
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
    // Mapear outros parâmetros para o SearchRequestDTO conforme necessário (TODO)

    return searchProductsUseCase.executeAsync(searchRequest)
        .thenApply(ResponseEntity::ok);
  }

  @PostMapping("/products/{productId}/index")
  public CompletableFuture<ResponseEntity<Void>> indexProduct(
      @PathVariable @NotBlank String productId,
      @Valid @RequestBody ProductDTO product) {

    return indexProductUseCase.executeAsync(product)
        .thenApply(v -> ResponseEntity.accepted().<Void>build())
        .exceptionally(ex -> {
          logger.error("Error indexing product", ex);
          return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        });
  }

  @GetMapping("/suggestions")
  public ResponseEntity<List<String>> getSuggestions(
      @RequestParam @NotBlank String term,
      @RequestParam(defaultValue = "10") @Min(1) @Max(20) Integer limit) {

    // Implementação das sugestões será adicionada posteriormente
    return ResponseEntity.ok(Collections.emptyList());
  }

}