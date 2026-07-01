package com.marketplace.search.catalog.infrastructure.messaging;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.marketplace.search.catalog.domain.entities.Product;
import com.marketplace.search.catalog.domain.exceptions.ProductAlreadyExistsException;
import com.marketplace.search.catalog.domain.ports.DistributedLockPort;
import com.marketplace.search.catalog.domain.repositories.ProductRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class KafkaProductEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProductEventConsumer.class);

    private final ProductRepository productRepository;
    private final DistributedLockPort lockPort;
    private final MeterRegistry meterRegistry;

    public KafkaProductEventConsumer(
            ProductRepository productRepository,
            DistributedLockPort lockPort,
            MeterRegistry meterRegistry) {
        this.productRepository = productRepository;
        this.lockPort = lockPort;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(topics = "${spring.kafka.template.default-topic:catalog.product.create.requests}", groupId = "${spring.kafka.consumer.group-id:catalog-service-group}")
    public void consume(Product product) {
        logger.info("Received product event from Kafka to save in DB: id={}", product.getId().value());

        // 1. Tenta adquirir Lock Distribuído
        boolean acquired = lockPort.tryAcquireLock(product.getId().value(), Duration.ofSeconds(15), Duration.ofSeconds(30));
        
        if (!acquired) {
            meterRegistry.counter("catalog.product.consumer.lock.denied.total").increment();
            logger.warn("Not possible to acquire lock for product {} after waiting. Another instance might be processing.", 
                product.getId().value());
            throw new ProductAlreadyExistsException(product.getId().value());
        }

        meterRegistry.counter("catalog.product.consumer.lock.acquired.total").increment();

        try {
            // 2. Verifica idempotência no Banco
            if (productRepository.existsById(product.getId().value())) {
                logger.info("Product {} already exists. Transparent idempotency: skipping creation.", 
                    product.getId().value());
                return;
            }

            Timer.Sample sample = Timer.start(meterRegistry);

            // 3. Salva
            productRepository.save(product);

            sample.stop(Timer.builder("catalog.db.operation.duration")
                .tag("operation", "save")
                .register(meterRegistry));

            logger.info("Product {} successfully saved to PostgreSQL from Kafka consumer. Debezium will capture this.", 
                product.getId().value());
                
        } finally {
            // 4. Libera o Lock
            lockPort.releaseLock(product.getId().value());
        }
    }
}
