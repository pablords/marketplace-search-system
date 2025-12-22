package com.marketplace.search.domain.repositories;

import com.marketplace.search.domain.entities.Product;

/**
 * Port (interface) para persistência de produtos.
 * Implementado pela camada de infraestrutura (Adapter).
 */
public interface ProductRepository {

  /**
   * Salva um produto no repositório.
   * 
   * @param product produto a ser salvo
   */
  void save(Product product);

  /**
   * Atualiza um produto existente.
   * 
   * @param product produto a ser atualizado
   */
  void update(Product product);

  /**
   * Deleta um produto por ID.
   * 
   * @param productId ID do produto
   */
  void delete(String productId);
}
