package com.marketplace.search.application.clients;

import java.net.URI;

/**
 * Porta (interface) para comunicação com o catalog-service.
 * Esta interface está na camada de aplicação para evitar dependências circulares.
 */
public interface CatalogServicePort {

  /**
   * Cria um novo produto no catalog-service.
   * 
   * @param productObject Objeto do produto (será serializado para JSON)
   * @return URI do produto criado
   * @throws CatalogServiceException se houver erro na comunicação
   */
  URI createProduct(Object productObject);

  /**
   * Exceção customizada para erros de comunicação com o catalog-service.
   */
  class CatalogServiceException extends RuntimeException {
    public CatalogServiceException(String message) {
      super(message);
    }

    public CatalogServiceException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}

