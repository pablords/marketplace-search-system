package com.marketplace.search.catalog.application.usecases;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import com.marketplace.search.catalog.application.commands.ProductCommand;
import com.marketplace.search.catalog.application.mappers.ProductMapper;
import com.marketplace.search.catalog.domain.entities.Product;
import com.marketplace.search.catalog.domain.exceptions.ProductAlreadyExistsException;
import com.marketplace.search.catalog.domain.repositories.ProductRepository;


/**
 * Caso de uso responsável por orquestrar o fluxo de criação de um produto.
 * O produto é persistido no PostgreSQL via ProductRepository (port) e o Debezium (CDC) 
 * automaticamente captura a mudança e publica no Kafka para indexação no Elasticsearch.
 * 
 * Implementa idempotência: se o produto já existe, lança exceção para evitar duplicação.
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
     * Implementa idempotência: verifica se o produto já existe antes de criar,
     * evitando duplicação no banco e consequente duplicação no Kafka.
     *
     * @param productDTO dados do produto informado pelo cliente
     * @throws ProductAlreadyExistsException se o produto já existe
     */
    @Transactional
    public void execute(ProductCommand productDTO) {
        logger.info("Received request for create product: id={}, title='{}'",
            productDTO.id(), productDTO.title());

        // Verifica idempotência: se o produto já existe, lança exceção
        if (productRepository.existsById(productDTO.id())) {
            logger.warn("Product {} already exists. Skipping creation to avoid duplication.", 
                productDTO.id());
            throw new ProductAlreadyExistsException(productDTO.id());
        }

        // Converte DTO para domínio
        Product product = productMapper.toDomain(productDTO);

        // Salva usando o repositório (port - implementado por adapter na infrastructure)
        productRepository.save(product);

        logger.info("Product {} saved to PostgreSQL. Debezium will capture and publish to Kafka.", 
            productDTO.id());
    }
}

