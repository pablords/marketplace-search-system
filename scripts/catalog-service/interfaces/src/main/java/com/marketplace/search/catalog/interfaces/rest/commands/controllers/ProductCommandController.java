package com.marketplace.search.catalog.interfaces.rest.commands.controllers;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marketplace.search.catalog.application.usecases.CreateProductUseCase;
import com.marketplace.search.catalog.interfaces.rest.commands.mappers.ProductMapper;
import com.marketplace.search.catalog.interfaces.rest.dtos.ProductDTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Endpoints de comando (escrita) relacionados a produtos.
 */
@RestController
@RequestMapping("/products")
@Validated
public class ProductCommandController implements ProductApiDoc {

  private static final Logger logger = LoggerFactory.getLogger(ProductCommandController.class);
  private final ProductMapper productMapper;
  private final CreateProductUseCase createProductUseCase;

  public ProductCommandController(ProductMapper productMapper, CreateProductUseCase createProductUseCase) {
    this.productMapper = productMapper;
    this.createProductUseCase = createProductUseCase;
  }

  @PostMapping
  public ResponseEntity<Void> create(@Valid @RequestBody ProductDTO productDTO) {
    logger.info("Iniciando criação do produto via API: {}", productDTO);

    createProductUseCase.execute(productMapper.toCommand(productDTO));

    URI location = URI.create("/products/" + productDTO.id());
    return ResponseEntity.created(location).build();
  }

}
