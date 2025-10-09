package com.marketplace.search.interfaces.rest;

import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v1/search")
@Validated
@Tag(name = "Search", description = "API de busca de produtos")
public class SearchController {

  private final SearchProductsUseCase searchProductsUseCase;
  private final IndexProductUseCase indexProductUseCase;

  @Autowired
  public SearchController(SearchProductsUseCase searchProductsUseCase,
      IndexProductUseCase indexProductUseCase) {
    this.searchProductsUseCase = searchProductsUseCase;
    this.indexProductUseCase = indexProductUseCase;
  }

  @GetMapping("/products")
  @Operation(summary = "Buscar produtos", description = "Realiza busca de produtos com filtros e ordenação")
  @ApiResponse(responseCode = "200", description = "Busca realizada com sucesso", content = @Content(schema = @Schema(implementation = SearchResultDTO.class)))
  @ApiResponse(responseCode = "400", description = "Parâmetros de busca inválidos")
  @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
  @Async("taskExecutor")
  public CompletableFuture<ResponseEntity<SearchResultDTO>> searchProducts(
      @Parameter(description = "Termo de busca", example = "smartphone samsung") @RequestParam(required = false) String query,

      @Parameter(description = "ID da categoria", example = "electronics") @RequestParam(required = false) String categoryId,

      @Parameter(description = "Nome da marca", example = "Samsung") @RequestParam(required = false) String brand,

      @Parameter(description = "Preço mínimo", example = "100.0") @RequestParam(required = false) Double minPrice,

      @Parameter(description = "Preço máximo", example = "1000.0") @RequestParam(required = false) Double maxPrice,

      @Parameter(description = "Condição do produto", example = "NEW") @RequestParam(required = false) String condition,

      @Parameter(description = "ID do vendedor", example = "seller123") @RequestParam(required = false) String sellerId,

      @Parameter(description = "Página (base 0)", example = "0") @RequestParam(defaultValue = "0") @Min(0) Integer page,

      @Parameter(description = "Tamanho da página", example = "20") @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size,

      @Parameter(description = "Campo de ordenação", example = "relevance") @RequestParam(defaultValue = "relevance") String sortBy,

      @Parameter(description = "Direção da ordenação", example = "desc") @RequestParam(defaultValue = "desc") String sortDirection,

      @Parameter(description = "ID do usuário (para personalização)", example = "user123") @RequestParam(required = false) String userId) {

    SearchRequestDTO searchRequest = new SearchRequestDTO();
    searchRequest.setQuery(query);
    // Mapear outros parâmetros para o SearchRequestDTO conforme necessário

    return CompletableFuture.supplyAsync(() -> {
      try {
        SearchResultDTO result = searchProductsUseCase.execute(searchRequest);
        return ResponseEntity.ok(result);
      } catch (Exception e) {
        SearchResultDTO errorResult = new SearchResultDTO();
        errorResult.setProducts(java.util.Collections.emptyList());
        errorResult.setTotalCount(0L);
        errorResult.setTotalPages(0);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
      }
    });
  }

  @PostMapping("/products/{productId}/index")
  @Operation(summary = "Indexar produto", description = "Adiciona ou atualiza produto no índice de busca")
  @ApiResponse(responseCode = "202", description = "Produto indexado com sucesso")
  @ApiResponse(responseCode = "400", description = "Dados do produto inválidos")
  @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
  @Async("indexingExecutor") // ← Usar executor específico para indexação
  public CompletableFuture<ResponseEntity<Void>> indexProduct(
      @Parameter(description = "ID do produto", example = "prod123") @PathVariable @NotBlank String productId,

      @Valid @RequestBody ProductDTO product) {

    return CompletableFuture.supplyAsync(() -> {
      try {
        indexProductUseCase.execute(product);
        return ResponseEntity.accepted().build();
      } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
      }
    });
  }

  @GetMapping("/suggestions")
  @Operation(summary = "Obter sugestões", description = "Retorna sugestões de busca baseadas no termo parcial")
  @ApiResponse(responseCode = "200", description = "Sugestões obtidas com sucesso")
  public ResponseEntity<java.util.List<String>> getSuggestions(
      @Parameter(description = "Termo parcial", example = "smartph") @RequestParam @NotBlank String term,

      @Parameter(description = "Número máximo de sugestões", example = "10") @RequestParam(defaultValue = "10") @Min(1) @Max(20) Integer limit) {

    // Implementação das sugestões será adicionada posteriormente
    return ResponseEntity.ok(java.util.Collections.emptyList());
  }

  @GetMapping("/health")
  @Operation(summary = "Health check", description = "Verifica se o serviço de busca está funcionando")
  public ResponseEntity<java.util.Map<String, Object>> health() {
    java.util.Map<String, Object> health = new java.util.HashMap<>();
    health.put("status", "UP");
    health.put("service", "search-api");
    health.put("timestamp", java.time.Instant.now());
    return ResponseEntity.ok(health);
  }
}