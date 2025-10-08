package com.marketplace.search.domain.repositories;

import com.marketplace.search.domain.entities.Product;
import com.marketplace.search.domain.valueobjects.ProductId;

import java.util.List;
import java.util.Optional;

/**
 * Interface do repositório para indexação de produtos
 */
public interface ProductIndexRepository {
    
    /**
     * Indexa um produto para busca
     */
    void indexProduct(Product product);
    
    /**
     * Indexa múltiplos produtos em lote
     */
    void indexProducts(List<Product> products);
    
    /**
     * Remove um produto do índice
     */
    void deleteProduct(ProductId productId);
    
    /**
     * Remove múltiplos produtos do índice
     */
    void deleteProducts(List<ProductId> productIds);
    
    /**
     * Atualiza um produto existente no índice
     */
    void updateProduct(Product product);
    
    /**
     * Busca um produto por ID
     */
    Optional<Product> findById(ProductId productId);
    
    /**
     * Busca múltiplos produtos por IDs
     */
    List<Product> findByIds(List<ProductId> productIds);
    
    /**
     * Verifica se um produto está indexado
     */
    boolean exists(ProductId productId);
    
    /**
     * Conta o número de produtos indexados
     */
    long count();
    
    /**
     * Atualiza o índice
     */
    void refreshIndex();
    
    /**
     * Remove todos os produtos do índice
     */
    void deleteAll();
    
    /**
     * Otimiza o índice para melhor performance
     */
    void optimize();
}