package com.marketplace.search.catalog.application.usecases;


import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marketplace.search.catalog.application.commands.ProductCommand;
import com.marketplace.search.catalog.application.mappers.ProductMapper;
import com.marketplace.search.catalog.domain.entities.Product;
import com.marketplace.search.catalog.domain.exceptions.ProductAlreadyExistsException;
import com.marketplace.search.catalog.domain.ports.DistributedLockPort;
import com.marketplace.search.catalog.domain.repositories.ProductRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;


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
    private final DistributedLockPort lockPort;
    private final MeterRegistry meterRegistry;
    
    // Semáforo para limitar a concorrência a 50 requisições simultâneas por pod
    private final java.util.concurrent.Semaphore semaphore = new java.util.concurrent.Semaphore(50);

    public CreateProductUseCase(
            ProductMapper productMapper,
            ProductRepository productRepository,
            DistributedLockPort lockPort,
            MeterRegistry meterRegistry) {
        this.productMapper = productMapper;
        this.productRepository = productRepository;
        this.lockPort = lockPort;
        this.meterRegistry = meterRegistry;
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
        
        if (!semaphore.tryAcquire()) {
            meterRegistry.counter("catalog.product.operations.rejected", "reason", "concurrency_limit").increment();
            throw new com.marketplace.search.catalog.domain.exceptions.TooManyRequestsException(
                "Muitas requisições simultâneas. Limite de concorrência (50) atingido no pod."
            );
        }

        try {
            meterRegistry.counter("catalog.product.operations.total", "operation", "create").increment();
            Timer.Sample sample = Timer.start(meterRegistry);

        logger.info("Received request for create product: id={}, title='{}'",
            productDTO.id(), productDTO.title());

        // 1. Tenta adquirir Lock Distribuído esperando até 15 segundos
        boolean acquired = lockPort.tryAcquireLock(productDTO.id(), Duration.ofSeconds(15), Duration.ofSeconds(30));
        
        if (!acquired) {
            meterRegistry.counter("catalog.product.lock.denied.total").increment();
            logger.warn("Not possible to acquire lock for product {} after waiting. Another instance might be processing.", 
                productDTO.id());
            // Se não conseguiu o lock no tempo limite, lançamos conflito (timeout).
            throw new ProductAlreadyExistsException(productDTO.id());
        }

        meterRegistry.counter("catalog.product.lock.acquired.total").increment();

        try {
            // 2. Verifica idempotência no Banco (Idempotência Transparente)
            if (productRepository.existsById(productDTO.id())) {
                logger.info("Product {} already exists. Transparent idempotency: skipping creation and returning success.", 
                    productDTO.id());
                // Retornamos silenciosamente para que o Controller devolva 201 Created (Sucesso)
                return;
            }

            // 3. Converte DTO para domínio e Salva
            Product product = productMapper.toDomain(productDTO);
            productRepository.save(product);

            sample.stop(Timer.builder("catalog.db.operation.duration")
                .tag("operation", "save")
                .register(meterRegistry));

            logger.info("Product {} saved to PostgreSQL. Debezium will capture and publish to Kafka.", 
                productDTO.id());
                
        } finally {
            // 4. Libera o Lock
            lockPort.releaseLock(productDTO.id());
        }
        } finally {
            semaphore.release();
        }
    }
}

