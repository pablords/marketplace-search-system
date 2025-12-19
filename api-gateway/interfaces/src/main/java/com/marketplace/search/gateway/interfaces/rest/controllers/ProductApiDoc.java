package com.marketplace.search.gateway.interfaces.rest.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.marketplace.search.gateway.interfaces.rest.dtos.ProductDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Products", description = "Operações de criação e manutenção de produtos")
public interface ProductApiDoc {

  @PostMapping
  @Operation(summary = "Criar produto", description = "Cria um produto no catalog-service. A indexação será feita automaticamente via Kafka CDC.", responses = {
      @ApiResponse(responseCode = "201", description = "Produto criado com sucesso", content = @Content(schema = @Schema(hidden = true))),
      @ApiResponse(responseCode = "400", description = "Dados inválidos"),
      @ApiResponse(responseCode = "502", description = "Erro ao comunicar com catalog-service")
  })
  public ResponseEntity<Void> create(@Valid @RequestBody ProductDTO productDTO);

}

