package com.marketplace.search.catalog.infrastructure.messaging;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
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
    private final Executor executor;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String dlqTopic;

    public KafkaProductEventConsumer(
            ProductRepository productRepository,
            DistributedLockPort lockPort,
            MeterRegistry meterRegistry,
            @Qualifier("applicationTaskExecutor") Executor executor,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${spring.kafka.template.dlq-topic:catalog.product.create.dlq}") String dlqTopic) {
        this.productRepository = productRepository;
        this.lockPort = lockPort;
        this.meterRegistry = meterRegistry;
        this.executor = executor;
        this.kafkaTemplate = kafkaTemplate;
        this.dlqTopic = dlqTopic;
    }

    @KafkaListener(topics = "${spring.kafka.template.default-topic:catalog.product.create.requests}", groupId = "${spring.kafka.consumer.group-id:catalog-service-group}")
    public void consume(List<Product> products) {
        if (products == null || products.isEmpty()) return;

        logger.info("Received batch of {} product events from Kafka", products.size());
        Timer.Sample sample = Timer.start(meterRegistry);

        // 1. Deduplicação do Lote
        Map<String, Product> uniqueProductsMap = new LinkedHashMap<>();
        for (Product p : products) {
            uniqueProductsMap.put(p.getId().value(), p);
        }
        List<Product> uniqueProducts = new ArrayList<>(uniqueProductsMap.values());

        // 2. Tenta adquirir Lock Distribuído em paralelo usando Virtual Threads
        List<CompletableFuture<Product>> lockFutures = uniqueProducts.stream()
            .map(product -> CompletableFuture.supplyAsync(() -> {
                boolean acquired = lockPort.tryAcquireLock(product.getId().value(), Duration.ofSeconds(5), Duration.ofSeconds(30));
                if (!acquired) {
                    meterRegistry.counter("catalog.product.consumer.lock.denied.total").increment();
                    logger.warn("Not possible to acquire lock for product {}. Another instance might be processing.", product.getId().value());
                    return null;
                }
                meterRegistry.counter("catalog.product.consumer.lock.acquired.total").increment();
                return product;
            }, executor))
            .toList();

        List<Product> lockedProducts = lockFutures.stream()
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .toList();

        if (lockedProducts.isEmpty()) {
            logger.warn("Could not acquire lock for any product in the batch. Exiting.");
            return;
        }

        try {
            // 3. Verifica idempotência em Massa no Banco
            List<String> productIds = lockedProducts.stream().map(p -> p.getId().value()).toList();
            List<String> existingIds = productRepository.findExistingIds(productIds);
            Set<String> existingIdsSet = existingIds.stream().collect(Collectors.toSet());

            List<Product> toSave = lockedProducts.stream()
                .filter(p -> !existingIdsSet.contains(p.getId().value()))
                .toList();

            if (toSave.isEmpty()) {
                logger.info("All products in the batch already exist. Transparent idempotency: skipping creation.");
                return;
            }

            try {
                // 4. Salva em Lote
                productRepository.saveAll(toSave);
                logger.info("Batch of {} products successfully saved to PostgreSQL.", toSave.size());
            } catch (Exception e) {
                logger.error("Failed to save batch to DB. Sending to DLQ. Error: {}", e.getMessage());
                for (Product failedProduct : toSave) {
                    kafkaTemplate.send(dlqTopic, failedProduct.getId().value(), failedProduct);
                }
            }

        } finally {
            // 5. Libera os Locks
            for (Product product : lockedProducts) {
                lockPort.releaseLock(product.getId().value());
            }
            sample.stop(Timer.builder("catalog.db.operation.duration")
                .tag("operation", "save_batch")
                .register(meterRegistry));
        }
    }
}
