package com.marketplace.search.gateway.interfaces.rest.controllers;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

import com.marketplace.search.gateway.interfaces.rest.dtos.SearchResultDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Tag(name = "Search", description = "API de busca de produtos")
public interface SearchApiDoc {

  @Operation(summary = "Buscar produtos", description = "Realiza busca de produtos com filtros e ordenação")
  @ApiResponse(responseCode = "200", description = "Busca realizada com sucesso", content = @Content(schema = @Schema(implementation = SearchResultDTO.class)))
  @ApiResponse(responseCode = "400", description = "Parâmetros de busca inválidos")
  @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
  CompletableFuture<ResponseEntity<SearchResultDTO>> searchProducts(
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

      @Parameter(description = "ID do usuário (para personalização)", example = "user123") @RequestParam(required = false) String userId);

  @Operation(summary = "Obter sugestões", description = "Retorna sugestões de busca baseadas no termo parcial")
  @ApiResponse(responseCode = "200", description = "Sugestões obtidas com sucesso")
  ResponseEntity<List<String>> getSuggestions(
      @Parameter(description = "Termo parcial", example = "smartph") @RequestParam @NotBlank String term,

      @Parameter(description = "Número máximo de sugestões", example = "10") @RequestParam(defaultValue = "10") @Min(1) @Max(20) Integer limit);

}

