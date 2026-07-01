package com.marketplace.search.catalog.domain.ports;

import com.marketplace.search.catalog.domain.entities.Product;

/**
 * Porta de saída para publicação de eventos de criação de produtos.
 */
public interface ProductEventProducerPort {
    void send(Product product);
}
