package com.marketplace.search.domain.repositories;

import com.marketplace.search.domain.events.DomainEvent;

import java.util.List;

/**
 * Interface para publicação de eventos de domínio
 */
public interface EventPublisher {

    /**
     * Publica um evento de domínio
     */
    void publish(DomainEvent event);

    /**
     * Publica um evento de forma assíncrona
     */
    void publishAsync(DomainEvent event);

    /**
     * Publica múltiplos eventos em batch
     */
    void publishBatch(List<DomainEvent> events);
}