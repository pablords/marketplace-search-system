package com.marketplace.search.interfaces.rest.commands.controllers;

import java.util.concurrent.CompletableFuture;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.marketplace.search.interfaces.rest.dtos.ProductDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Tag(name = "Products", description = "Operações de criação e manutenção de produtos")
public interface ProductApiDoc {

  @PostMapping
  @Operation(summary = "Criar produto", description = "Recebe os dados do produto e dispara o evento de criação para indexação", responses = {
      @ApiResponse(responseCode = "201", description = "Produto aceito para processamento", content = @Content(schema = @Schema(hidden = true))),
      @ApiResponse(responseCode = "400", description = "Dados inválidos")
  })
  public ResponseEntity<Void> create(@Valid @RequestBody ProductDTO productDTO);

    @Operation(summary = "Indexar produto", description = "Adiciona ou atualiza produto no índice de busca")
  @ApiResponse(responseCode = "202", description = "Produto indexado com sucesso")
  @ApiResponse(responseCode = "400", description = "Dados do produto inválidos")
  @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
  CompletableFuture<ResponseEntity<Void>> indexProduct(
      @Parameter(description = "ID do produto", example = "prod123") @PathVariable @NotBlank String productId,

      @Valid @RequestBody ProductDTO product);

}
