package com.marketplace.search.catalog.infrastructure.messaging;

import java.time.Duration;
import java.util.List;

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
public class KafkaProductEventDlqConsumer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProductEventDlqConsumer.class);

    private final ProductRepository productRepository;
    private final DistributedLockPort lockPort;
    private final MeterRegistry meterRegistry;

    public KafkaProductEventDlqConsumer(
            ProductRepository productRepository,
            DistributedLockPort lockPort,
            MeterRegistry meterRegistry) {
        this.productRepository = productRepository;
        this.lockPort = lockPort;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
        topics = "${spring.kafka.template.dlq-topic:catalog.product.create.dlq}", 
        groupId = "catalog-service-dlq-group"
    )
    public void consumeDlq(List<Product> products) {
        if (products == null || products.isEmpty()) return;
        
        logger.info("DLQ: Received batch of {} product events to process sequentially", products.size());

        for (Product product : products) {
            logger.info("DLQ: Processing product id={}", product.getId().value());

            // 1. Tenta adquirir Lock Distribuído
            boolean acquired = lockPort.tryAcquireLock(product.getId().value(), Duration.ofSeconds(15), Duration.ofSeconds(30));
            
            if (!acquired) {
                meterRegistry.counter("catalog.product.consumer.dlq.lock.denied.total").increment();
                logger.warn("DLQ: Not possible to acquire lock for product {} after waiting. Skipping to next.", 
                    product.getId().value());
                continue; // Skip this one, let others process
            }

            meterRegistry.counter("catalog.product.consumer.dlq.lock.acquired.total").increment();

            try {
                // 2. Verifica idempotência no Banco
                if (productRepository.existsById(product.getId().value())) {
                    logger.info("DLQ: Product {} already exists. Transparent idempotency: skipping creation.", 
                        product.getId().value());
                    continue;
                }

                Timer.Sample sample = Timer.start(meterRegistry);

                // 3. Salva
                productRepository.save(product);

                sample.stop(Timer.builder("catalog.db.operation.duration")
                    .tag("operation", "save_dlq")
                    .register(meterRegistry));

                logger.info("DLQ: Product {} successfully saved to PostgreSQL.", product.getId().value());
                    
            } catch (Exception e) {
                logger.error("DLQ: Failed to save product {} to PostgreSQL. Error: {}", product.getId().value(), e.getMessage());
                // We don't throw to avoid failing the whole batch, we just log it and maybe manual intervention is needed.
                // It's the end of the line for this DLQ item.
            } finally {
                // 4. Libera o Lock
                lockPort.releaseLock(product.getId().value());
            }
        }
    }
}
