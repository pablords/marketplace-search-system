package com.marketplace.search.domain.events;

import com.marketplace.search.domain.entities.Product;

/**
 * Evento disparado quando um produto é atualizado
 */
public class ProductUpdatedEvent extends DomainEvent {
    
    private final Product product;
    private final Product previousVersion;

    public ProductUpdatedEvent(Product product, Product previousVersion) {
        super(product.getId());
        this.product = product;
        this.previousVersion = previousVersion;
    }

    public Product getProduct() {
        return product;
    }

    public Product getPreviousVersion() {
        return previousVersion;
    }

    @Override
    public String toString() {
        return "ProductUpdatedEvent{" +
                "productId=" + getProductId() +
                ", title='" + product.getInfo().getTitle() + '\'' +
                ", occurredOn=" + getOccurredOn() +
                '}';
    }
}