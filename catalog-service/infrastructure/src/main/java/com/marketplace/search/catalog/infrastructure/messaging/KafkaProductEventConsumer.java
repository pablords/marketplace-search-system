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
import com.marketplace.search.catalog.domain.ports.DistributedLockPort;
import com.marketplace.search.catalog.domain.repositories.ProductRepository;
import com.marketplace.search.catalog.infrastructure.avro.ProductAvro;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class KafkaProductEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProductEventConsumer.class);

    private final ProductRepository productRepository;
    private final DistributedLockPort lockPort;
    private final MeterRegistry meterRegistry;
    private final Executor executor;
    private final KafkaTemplate<String, ProductAvro> kafkaTemplate;
    private final String dlqTopic;

    public KafkaProductEventConsumer(
            ProductRepository productRepository,
            DistributedLockPort lockPort,
            MeterRegistry meterRegistry,
            @Qualifier("applicationTaskExecutor") Executor executor,
            KafkaTemplate<String, ProductAvro> kafkaTemplate,
            @Value("${spring.kafka.template.dlq-topic:catalog.product.create.dlq}") String dlqTopic) {
        this.productRepository = productRepository;
        this.lockPort = lockPort;
        this.meterRegistry = meterRegistry;
        this.executor = executor;
        this.kafkaTemplate = kafkaTemplate;
        this.dlqTopic = dlqTopic;
    }

    @KafkaListener(topics = "${spring.kafka.template.default-topic:catalog.product.create.requests}", groupId = "${spring.kafka.consumer.group-id:catalog-service-group}")
    public void consume(List<ProductAvro> productsAvro) {
        if (productsAvro == null || productsAvro.isEmpty()) return;

        logger.info("Received batch of {} product events from Kafka (Avro)", productsAvro.size());
        Timer.Sample sample = Timer.start(meterRegistry);

        List<Product> products = new ArrayList<>();
        for (ProductAvro avro : productsAvro) {
            try {
                products.add(mapAvroToProduct(avro));
            } catch (Exception e) {
                logger.error("Error mapping ProductAvro to domain Product: {}", e.getMessage(), e);
            }
        }

        if (products.isEmpty()) {
            logger.warn("No products could be successfully mapped from Avro batch.");
            return;
        }

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
                    // Send to DLQ (converting back to Avro)
                    try {
                        ProductAvro failedAvro = productsAvro.stream()
                            .filter(a -> a.getId().toString().equals(failedProduct.getId().value()))
                            .findFirst()
                            .orElse(mapProductToAvro(failedProduct));
                        kafkaTemplate.send(dlqTopic, failedProduct.getId().value(), failedAvro);
                    } catch (Exception ex) {
                        logger.error("Failed to send product to DLQ: {}", ex.getMessage());
                    }
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

    private Product mapAvroToProduct(ProductAvro avro) {
        com.marketplace.search.catalog.domain.valueobjects.ProductId productId = 
            new com.marketplace.search.catalog.domain.valueobjects.ProductId(avro.getId().toString());

        com.marketplace.search.catalog.domain.entities.Category category = 
            new com.marketplace.search.catalog.domain.entities.Category(
                avro.getCategory().getId().toString(),
                avro.getCategory().getName() != null ? avro.getCategory().getName().toString() : "Unknown",
                null,
                avro.getCategory().getName() != null ? avro.getCategory().getName().toString().toLowerCase() : "unknown"
            );

        com.marketplace.search.catalog.domain.valueobjects.Brand brand = 
            new com.marketplace.search.catalog.domain.valueobjects.Brand(
                avro.getBrand().getId().toString(),
                avro.getBrand().getName() != null ? avro.getBrand().getName().toString() : "Unknown",
                null
            );

        com.marketplace.search.catalog.domain.valueobjects.ProductInfo info = 
            new com.marketplace.search.catalog.domain.valueobjects.ProductInfo(
                avro.getTitle().toString(),
                avro.getDescription().toString(),
                new java.math.BigDecimal(avro.getPrice().toString()),
                avro.getCurrency().toString(),
                category,
                brand,
                convertCharSequenceList(avro.getImages()),
                convertCharSequenceSet(avro.getAttributes()),
                convertCharSequenceSet(avro.getTags())
            );

        com.marketplace.search.catalog.domain.entities.Seller seller = 
            new com.marketplace.search.catalog.domain.entities.Seller(
                avro.getSeller().getId().toString(),
                avro.getSeller().getName() != null ? avro.getSeller().getName().toString() : "Unknown",
                com.marketplace.search.catalog.domain.valueobjects.SellerType.MERCADO_LIDER,
                new com.marketplace.search.catalog.domain.valueobjects.SellerReputation(5.0, 100, 100, 0, 0, 0.0, 1.0),
                com.marketplace.search.catalog.domain.valueobjects.SellerStatus.ACTIVE,
                java.time.Instant.now()
            );

        com.marketplace.search.catalog.domain.valueobjects.ProductMetrics metrics = 
            new com.marketplace.search.catalog.domain.valueobjects.ProductMetrics(
                (int) avro.getMetrics().getViews(),
                (int) avro.getMetrics().getSales(),
                0,
                avro.getMetrics().getScore(),
                (int) avro.getMetrics().getStock(),
                0.0,
                null,
                null,
                0,
                0.0,
                0.0
            );

        String statusStr = avro.getStatus().toString();
        com.marketplace.search.catalog.domain.valueobjects.ProductStatus status;
        if ("ACTIVE".equalsIgnoreCase(statusStr)) {
            status = com.marketplace.search.catalog.domain.valueobjects.ProductStatus.active(avro.getMetrics().getStock() > 0);
        } else if ("SUSPENDED".equalsIgnoreCase(statusStr)) {
            status = com.marketplace.search.catalog.domain.valueobjects.ProductStatus.suspended("Suspended via import");
        } else {
            status = com.marketplace.search.catalog.domain.valueobjects.ProductStatus.inactive();
        }

        return Product.builder()
            .id(productId)
            .info(info)
            .seller(seller)
            .metrics(metrics)
            .status(status)
            .createdAt(java.time.Instant.ofEpochMilli(avro.getCreatedAt()))
            .updatedAt(java.time.Instant.ofEpochMilli(avro.getUpdatedAt()))
            .build();
    }

    private ProductAvro mapProductToAvro(Product product) {
        return ProductAvro.newBuilder()
            .setId(product.getId().value())
            .setTitle(product.getInfo().getTitle())
            .setDescription(product.getInfo().getDescription())
            .setPrice(product.getInfo().getPrice().toString())
            .setCurrency(product.getInfo().getCurrency())
            .setCategory(com.marketplace.search.catalog.infrastructure.avro.CategoryAvro.newBuilder()
                .setId(product.getInfo().getCategory().getId())
                .setName(product.getInfo().getCategory().getName())
                .build())
            .setBrand(com.marketplace.search.catalog.infrastructure.avro.BrandAvro.newBuilder()
                .setId(product.getInfo().getBrand().id())
                .setName(product.getInfo().getBrand().name())
                .build())
            .setSeller(com.marketplace.search.catalog.infrastructure.avro.SellerAvro.newBuilder()
                .setId(product.getSeller().getId())
                .setName(product.getSeller().getName())
                .build())
            .setMetrics(com.marketplace.search.catalog.infrastructure.avro.MetricsAvro.newBuilder()
                .setViews(product.getMetrics().totalViews())
                .setSales(product.getMetrics().totalSales())
                .setStock(product.getMetrics().stockQuantity())
                .setScore(product.getMetrics().averageRating())
                .build())
            .setStatus(product.getStatus().getStateName())
            .setImages(product.getInfo().getImages() != null ? new ArrayList<>(product.getInfo().getImages()) : new ArrayList<>())
            .setAttributes(product.getInfo().getAttributes() != null ? new ArrayList<>(product.getInfo().getAttributes()) : new ArrayList<>())
            .setTags(product.getInfo().getTags() != null ? new ArrayList<>(product.getInfo().getTags()) : new ArrayList<>())
            .setCreatedAt(product.getCreatedAt().toEpochMilli())
            .setUpdatedAt(product.getUpdatedAt().toEpochMilli())
            .build();
    }

    private List<String> convertCharSequenceList(List<CharSequence> list) {
        if (list == null) return new java.util.ArrayList<>();
        List<String> result = new java.util.ArrayList<>();
        for (CharSequence cs : list) {
            result.add(cs.toString());
        }
        return result;
    }

    private java.util.Set<String> convertCharSequenceSet(java.util.Collection<CharSequence> col) {
        if (col == null) return new java.util.HashSet<>();
        java.util.Set<String> result = new java.util.HashSet<>();
        for (CharSequence cs : col) {
            result.add(cs.toString());
        }
        return result;
    }
}
