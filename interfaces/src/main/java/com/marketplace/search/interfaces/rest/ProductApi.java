package com.marketplace.search.interfaces.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.marketplace.search.application.dto.ProductDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Products", description = "Operações de criação e manutenção de produtos")
public interface ProductApi {

  @PostMapping
  @Operation(summary = "Criar produto", description = "Recebe os dados do produto e dispara o evento de criação para indexação", responses = {
      @ApiResponse(responseCode = "201", description = "Produto aceito para processamento", content = @Content(schema = @Schema(hidden = true))),
      @ApiResponse(responseCode = "400", description = "Dados inválidos")
  })
  public ResponseEntity<Void> create(@Valid @RequestBody ProductDTO productDTO);

}
