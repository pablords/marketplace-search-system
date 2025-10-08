package com.marketplace.search.domain.events;

import com.marketplace.search.domain.entities.Product;

/**
 * Evento disparado quando um produto é criado
 */
public class ProductCreatedEvent extends DomainEvent {
    
    private final Product product;

    public ProductCreatedEvent(Product product) {
        super(product.getId());
        this.product = product;
    }

    public Product getProduct() {
        return product;
    }

    @Override
    public String toString() {
        return "ProductCreatedEvent{" +
                "productId=" + getProductId() +
                ", title='" + product.getInfo().getTitle() + '\'' +
                ", occurredOn=" + getOccurredOn() +
                '}';
    }
}