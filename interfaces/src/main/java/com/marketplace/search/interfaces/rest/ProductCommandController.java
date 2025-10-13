package com.marketplace.search.interfaces.rest;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marketplace.search.application.dto.ProductDTO;
import com.marketplace.search.application.usecases.CreateProductUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Endpoints de comando (escrita) relacionados a produtos.
 */
@RestController
@RequestMapping("/products")
@Validated
@Tag(name = "Products", description = "Operações de criação e manutenção de produtos")
public class ProductCommandController {

    private static final Logger logger = LoggerFactory.getLogger(ProductCommandController.class);

    private final CreateProductUseCase createProductUseCase;

    public ProductCommandController(CreateProductUseCase createProductUseCase) {
        this.createProductUseCase = createProductUseCase;
    }

    @PostMapping
    @Operation(summary = "Criar produto",
        description = "Recebe os dados do produto e dispara o evento de criação para indexação",
        responses = {
            @ApiResponse(responseCode = "201", description = "Produto aceito para processamento",
                content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
        })
    public ResponseEntity<Void> create(@Valid @RequestBody ProductDTO productDTO) {
        logger.info("Iniciando criação do produto via API: {}", productDTO);

        createProductUseCase.execute(productDTO);

        URI location = URI.create("/products/" + productDTO.getId());
        return ResponseEntity.created(location).build();
    }
}
