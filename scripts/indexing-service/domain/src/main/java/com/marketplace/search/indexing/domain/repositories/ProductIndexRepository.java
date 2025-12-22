package com.marketplace.search.indexing.domain.repositories;

import java.util.List;

import com.marketplace.search.indexing.domain.entities.Product;
import com.marketplace.search.indexing.domain.valueobjects.ProductId;

/**
 * Interface do repositório para indexação de produtos
 */
public interface ProductIndexRepository {
  void createKnnIndex(int vectorDim) throws Exception;

  void indexDocumentsBatch(List<Product> products) throws Exception;

  boolean exists(ProductId productId) throws Exception;

  void updateProduct(Product product) throws Exception;

  void indexProduct(Product product) throws Exception;
}