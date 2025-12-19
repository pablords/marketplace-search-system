package com.marketplace.search.interfaces.rest.commands.controllers;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marketplace.search.application.clients.CatalogServicePort;
import com.marketplace.search.interfaces.rest.dtos.ProductDTO;

import jakarta.validation.Valid;

/**
 * Endpoints de comando (escrita) relacionados a produtos.
 * Delega operações para o catalog-service via HTTP.
 */
@RestController
@RequestMapping("/products")
@Validated
public class ProductCommandController implements ProductApiDoc {

  private static final Logger logger = LoggerFactory.getLogger(ProductCommandController.class);
  private final CatalogServicePort catalogServicePort;

  public ProductCommandController(CatalogServicePort catalogServicePort) {
    this.catalogServicePort = catalogServicePort;
  }

  @PostMapping
  public ResponseEntity<Void> create(@Valid @RequestBody ProductDTO productDTO) {
    logger.info("Iniciando criação do produto via API: {}", productDTO.id());

    try {
      URI location = catalogServicePort.createProduct(productDTO);

      if (location == null) {
        // Fallback: construir URI baseado no ID se não retornado pelo serviço
        location = URI.create("/products/" + productDTO.id());
      }

      logger.info("Produto criado com sucesso no catalog-service: {}", productDTO.id());
      return ResponseEntity.created(location).build();
    } catch (CatalogServicePort.CatalogServiceException ex) {
      logger.error("Erro ao criar produto no catalog-service: {}", ex.getMessage(), ex);
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
    } catch (Exception ex) {
      logger.error("Erro inesperado ao criar produto: {}", ex.getMessage(), ex);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

}
