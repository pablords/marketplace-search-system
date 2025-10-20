package com.marketplace.search.domain.events;

import com.marketplace.search.domain.valueobjects.ProductId;

/**
 * Evento disparado quando um produto é deletado
 */
public class ProductDeletedEvent extends DomainEvent {
    
    public ProductDeletedEvent(String productId) {
        super(new ProductId(productId));
    }

    @Override
    public String toString() {
        return "ProductDeletedEvent{" +
                "productId=" + getProductId() +
                ", occurredOn=" + getOccurredOn() +
                '}';
    }
}
