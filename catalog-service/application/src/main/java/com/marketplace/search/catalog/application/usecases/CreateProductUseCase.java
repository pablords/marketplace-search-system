package com.marketplace.search.catalog.application.usecases;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.marketplace.search.catalog.application.commands.ProductCommand;
import com.marketplace.search.catalog.application.mappers.ProductMapper;
import com.marketplace.search.catalog.domain.entities.Product;
import com.marketplace.search.catalog.domain.ports.ProductEventProducerPort;

import org.springframework.beans.factory.annotation.Value;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;


/**
 * Caso de uso responsável por orquestrar o fluxo de criação de um produto.
 * O produto é enviado para um tópico Kafka (assíncrono).
 * Um consumer local o processará em background gravando no PostgreSQL.
 * O Debezium continuará lendo do PostgreSQL e publicando no Elasticsearch.
 */
@Service
public class CreateProductUseCase {

    private static final Logger logger = LoggerFactory.getLogger(CreateProductUseCase.class);

    private final ProductMapper productMapper;
    private final ProductEventProducerPort productEventProducerPort;
    private final MeterRegistry meterRegistry;
    
    // Semáforo para limitar a concorrência de requisições simultâneas por pod
    private final java.util.concurrent.Semaphore semaphore;

    public CreateProductUseCase(
            ProductMapper productMapper,
            ProductEventProducerPort productEventProducerPort,
            MeterRegistry meterRegistry,
            @Value("${concurrency.limit:50}") int concurrencyLimit) {
        this.productMapper = productMapper;
        this.productEventProducerPort = productEventProducerPort;
        this.meterRegistry = meterRegistry;
        this.semaphore = new java.util.concurrent.Semaphore(concurrencyLimit);
        logger.info("Inicializando CreateProductUseCase com limite de concorrência (semáforo) = {}", concurrencyLimit);
    }

    /**
     * Envia o comando de criação de produto para o Kafka de forma assíncrona.
     * O consumer irá processar a idempotência e gravação no banco.
     *
     * @param productDTO dados do produto informado pelo cliente
     */
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

            logger.info("Received request for create product (Async): id={}, title='{}'",
                productDTO.id(), productDTO.title());

            // 1. Converte DTO para domínio
            Product product = productMapper.toDomain(productDTO);
            
            // 2. Envia para o Kafka
            productEventProducerPort.send(product);

            sample.stop(Timer.builder("catalog.kafka.operation.duration")
                .tag("operation", "send_create_request")
                .register(meterRegistry));

            logger.info("Product {} creation request sent to Kafka.", productDTO.id());
                
        } finally {
            semaphore.release();
        }
    }

}

