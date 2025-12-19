package com.marketplace.search.gateway.interfaces.rest.controllers;

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

import com.marketplace.search.gateway.interfaces.rest.clients.SearchServicePort;
import com.marketplace.search.gateway.interfaces.rest.dtos.SearchResultDTO;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/search")
@Validated
public class SearchGatewayController implements SearchApiDoc {

  private final SearchServicePort searchServicePort;
  private static final Logger logger = LoggerFactory.getLogger(SearchGatewayController.class);

  public SearchGatewayController(SearchServicePort searchServicePort) {
    this.searchServicePort = searchServicePort;
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

    logger.info("Received search request via API Gateway: query={}", query);

    if (query == null || query.trim().isEmpty()) {
      throw new IllegalArgumentException("Search terms cannot be null or empty");
    }

    try {
      Object result = searchServicePort.searchProducts(
          query.trim(),
          categoryId,
          page,
          size,
          sortBy,
          userId);
      
      SearchResultDTO searchResult = (SearchResultDTO) result;
      logger.info("Search completed: totalResults={}", searchResult.totalCount());
      return CompletableFuture.completedFuture(ResponseEntity.ok(searchResult));
    } catch (SearchServicePort.SearchServiceException ex) {
      logger.error("Error searching products via search-service", ex);
      return CompletableFuture.failedFuture(new RuntimeException("Failed to search products: " + ex.getMessage(), ex));
    }
  }

  @GetMapping("/suggestions")
  public ResponseEntity<List<String>> getSuggestions(
      @RequestParam @NotBlank String term,
      @RequestParam(defaultValue = "10") @Min(1) @Max(20) Integer limit) {

    logger.info("Received suggestions request via API Gateway: term={}", term);

    try {
      List<String> suggestions = searchServicePort.getSuggestions(term, limit);
      logger.info("Suggestions completed: count={}", suggestions != null ? suggestions.size() : 0);
      return ResponseEntity.ok(suggestions != null ? suggestions : List.of());
    } catch (SearchServicePort.SearchServiceException ex) {
      logger.error("Error getting suggestions via search-service", ex);
      return ResponseEntity.ok(List.of());
    } catch (Exception ex) {
      logger.error("Unexpected error getting suggestions", ex);
      return ResponseEntity.ok(List.of());
    }
  }

}

