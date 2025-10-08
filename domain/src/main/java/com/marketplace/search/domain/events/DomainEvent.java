package com.marketplace.search.domain.events;

import com.marketplace.search.domain.valueobjects.ProductId;

import java.time.Instant;
import java.util.Objects;

/**
 * Evento base para todos os eventos de domínio
 */
public abstract class DomainEvent {
    
    private final String eventId;
    private final Instant occurredOn;
    private final ProductId productId;

    protected DomainEvent(ProductId productId) {
        this.eventId = java.util.UUID.randomUUID().toString();
        this.occurredOn = Instant.now();
        this.productId = Objects.requireNonNull(productId, "Product ID cannot be null");
    }

    public String getEventId() { return eventId; }
    public Instant getOccurredOn() { return occurredOn; }
    public ProductId getProductId() { return productId; }
    
    public String getAggregateId() {
        return productId.getValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DomainEvent that = (DomainEvent) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }
}