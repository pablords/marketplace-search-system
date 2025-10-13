package com.marketplace.search.application.usecases;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.marketplace.search.application.dto.ProductDTO;
import com.marketplace.search.application.mappers.ProductMapper;
import com.marketplace.search.domain.entities.Product;
import com.marketplace.search.domain.events.ProductCreatedEvent;
import com.marketplace.search.domain.repositories.EventPublisher;

/**
 * Caso de uso responsável por orquestrar o fluxo de criação de um produto.
 * O produto é publicado como evento de domínio para que outras camadas
 * (como a indexação no Elasticsearch) possam reagir via Kafka.
 */
@Service
public class CreateProductUseCase {

    private static final Logger logger = LoggerFactory.getLogger(CreateProductUseCase.class);

    private final ProductMapper productMapper;
    private final EventPublisher eventPublisher;

    public CreateProductUseCase(ProductMapper productMapper, EventPublisher eventPublisher) {
        this.productMapper = productMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Cria um novo produto, publicando o evento correspondente na fila de Kafka.
     *
     * @param productDTO dados do produto informado pelo cliente
     */
    public void execute(ProductDTO productDTO) {
        logger.info("Received request for create product: id={}, title='{}'",
            productDTO.getId(), productDTO.getTitle());

        Product product = productMapper.toDomain(productDTO);

        ProductCreatedEvent event = new ProductCreatedEvent(product);
        eventPublisher.publish(event);

        logger.info("Event of create product {} publish with success", productDTO.getId());
    }
}
