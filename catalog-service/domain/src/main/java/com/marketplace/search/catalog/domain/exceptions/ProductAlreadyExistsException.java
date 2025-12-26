package com.marketplace.search.catalog.domain.exceptions;

/**
 * Exceção lançada quando se tenta criar um produto que já existe.
 * Usada para garantir idempotência na criação de produtos.
 */
public class ProductAlreadyExistsException extends RuntimeException {
    
    private final String productId;
    
    public ProductAlreadyExistsException(String productId) {
        super(String.format("Produto com ID '%s' já existe", productId));
        this.productId = productId;
    }
    
    public String getProductId() {
        return productId;
    }
}

