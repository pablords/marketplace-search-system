package com.marketplace.search.domain.events;

import com.marketplace.search.domain.valueobjects.ProductId;

/**
 * Classe base para eventos relacionados a produtos
 */
public abstract class ProductEvent extends DomainEvent {

    protected ProductEvent(ProductId productId) {
        super(productId);
    }
}