package com.marketplace.search.application.usecases;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marketplace.search.application.dto.ProductDTO;
import com.marketplace.search.application.mappers.ProductMapper;
import com.marketplace.search.domain.entities.Product;
import com.marketplace.search.domain.repositories.ProductRepository;

/**
 * Caso de uso responsável por orquestrar o fluxo de criação de um produto.
 * O produto é persistido no PostgreSQL via ProductRepository (port) e o Debezium (CDC) 
 * automaticamente captura a mudança e publica no Kafka para indexação no Elasticsearch.
 */
@Service
public class CreateProductUseCase {

    private static final Logger logger = LoggerFactory.getLogger(CreateProductUseCase.class);

    private final ProductMapper productMapper;
    private final ProductRepository productRepository;

    public CreateProductUseCase(
            ProductMapper productMapper,
            ProductRepository productRepository) {
        this.productMapper = productMapper;
        this.productRepository = productRepository;
    }

    /**
     * Cria um novo produto salvando no PostgreSQL.
     * O Debezium captura automaticamente a inserção via CDC e publica no Kafka.
     *
     * @param productDTO dados do produto informado pelo cliente
     */
    @Transactional
    public void execute(ProductDTO productDTO) {
        logger.info("Received request for create product: id={}, title='{}'",
            productDTO.id(), productDTO.title());

        // Converte DTO para domínio
        Product product = productMapper.toDomain(productDTO);

        // Salva usando o repositório (port - implementado por adapter na infrastructure)
        productRepository.save(product);

        logger.info("Product {} saved to PostgreSQL. Debezium will capture and publish to Kafka.", 
            productDTO.id());
    }
}

