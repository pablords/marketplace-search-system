package com.marketplace.search.interfaces.kafka;

import com.marketplace.search.application.dto.ProductDTO;
import com.marketplace.search.application.usecases.IndexProductUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class ProductEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ProductEventConsumer.class);

    private final IndexProductUseCase indexProductUseCase;
    private final ObjectMapper objectMapper;

    @Autowired
    public ProductEventConsumer(IndexProductUseCase indexProductUseCase, ObjectMapper objectMapper) {
        this.indexProductUseCase = indexProductUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "${kafka.topics.product-events}",
        groupId = "${kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleProductEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            @Header(value = "eventType", required = false) String eventType,
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {

        try {
            logger.info("Recebido evento do produto - Topic: {}, Partition: {}, Offset: {}, Timestamp: {}, EventType: {}", 
                       topic, partition, record.offset(), Instant.ofEpochMilli(timestamp), eventType);

            ProductEvent productEvent = objectMapper.readValue(message, ProductEvent.class);
            
            CompletableFuture<Void> processingFuture = switch (productEvent.getEventType()) {
                case PRODUCT_CREATED, PRODUCT_UPDATED -> processProductUpsert(productEvent);
                case PRODUCT_DELETED -> processProductDeletion(productEvent);
                case INVENTORY_UPDATED -> processInventoryUpdate(productEvent);
                case PRICE_UPDATED -> processPriceUpdate(productEvent);
                default -> {
                    logger.warn("Tipo de evento não suportado: {}", productEvent.getEventType());
                    yield CompletableFuture.completedFuture(null);
                }
            };

            processingFuture
                .thenRun(() -> {
                    logger.info("Evento processado com sucesso - ProductId: {}, EventType: {}", 
                               productEvent.getProductId(), productEvent.getEventType());
                    acknowledgment.acknowledge();
                })
                .exceptionally(throwable -> {
                    logger.error("Erro ao processar evento - ProductId: {}, EventType: {}, Error: {}", 
                                productEvent.getProductId(), productEvent.getEventType(), throwable.getMessage(), throwable);
                    // Em caso de erro, não fazer acknowledge para reprocessar a mensagem
                    return null;
                });

        } catch (Exception e) {
            logger.error("Erro ao parsear evento do Kafka - Message: {}, Error: {}", message, e.getMessage(), e);
            // Em caso de erro de parsing, fazer acknowledge para evitar loop infinito
            acknowledgment.acknowledge();
        }
    }

    private CompletableFuture<Void> processProductUpsert(ProductEvent productEvent) {
        logger.debug("Processando criação/atualização do produto: {}", productEvent.getProductId());
        
        ProductDTO productDTO = mapToProductDTO(productEvent);
        return indexProductUseCase.execute(productDTO);
    }

    private CompletableFuture<Void> processProductDeletion(ProductEvent productEvent) {
        logger.debug("Processando deleção do produto: {}", productEvent.getProductId());
        
        // TODO: Implementar remoção do índice
        // return deleteProductUseCase.execute(productEvent.getProductId());
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> processInventoryUpdate(ProductEvent productEvent) {
        logger.debug("Processando atualização de estoque do produto: {}", productEvent.getProductId());
        
        // Para atualização de estoque, podemos fazer uma atualização parcial
        ProductDTO productDTO = ProductDTO.builder()
                .id(productEvent.getProductId())
                .availableQuantity(productEvent.getPayload().getAvailableQuantity())
                .lastModified(productEvent.getTimestamp())
                .build();
        
        return indexProductUseCase.execute(productDTO);
    }

    private CompletableFuture<Void> processPriceUpdate(ProductEvent productEvent) {
        logger.debug("Processando atualização de preço do produto: {}", productEvent.getProductId());
        
        // Para atualização de preço, podemos fazer uma atualização parcial
        ProductDTO productDTO = ProductDTO.builder()
                .id(productEvent.getProductId())
                .price(productEvent.getPayload().getPrice())
                .currency(productEvent.getPayload().getCurrency())
                .lastModified(productEvent.getTimestamp())
                .build();
        
        return indexProductUseCase.execute(productDTO);
    }

    private ProductDTO mapToProductDTO(ProductEvent productEvent) {
        ProductEventPayload payload = productEvent.getPayload();
        
        return ProductDTO.builder()
                .id(productEvent.getProductId())
                .title(payload.getTitle())
                .description(payload.getDescription())
                .price(payload.getPrice())
                .currency(payload.getCurrency())
                .availableQuantity(payload.getAvailableQuantity())
                .condition(payload.getCondition())
                .status(payload.getStatus())
                .categoryId(payload.getCategoryId())
                .categoryName(payload.getCategoryName())
                .brandName(payload.getBrandName())
                .sellerId(payload.getSellerId())
                .sellerNickname(payload.getSellerNickname())
                .sellerReputation(payload.getSellerReputation())
                .attributes(payload.getAttributes())
                .totalSold(payload.getTotalSold())
                .viewCount(payload.getViewCount())
                .conversionRate(payload.getConversionRate())
                .averageRating(payload.getAverageRating())
                .reviewCount(payload.getReviewCount())
                .createdAt(payload.getCreatedAt())
                .lastModified(productEvent.getTimestamp())
                .build();
    }

    // Classes internas para deserialização do evento
    public static class ProductEvent {
        private String productId;
        private ProductEventType eventType;
        private ProductEventPayload payload;
        private Instant timestamp;
        private String source;

        // Getters e Setters
        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }

        public ProductEventType getEventType() { return eventType; }
        public void setEventType(ProductEventType eventType) { this.eventType = eventType; }

        public ProductEventPayload getPayload() { return payload; }
        public void setPayload(ProductEventPayload payload) { this.payload = payload; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    public enum ProductEventType {
        PRODUCT_CREATED,
        PRODUCT_UPDATED,
        PRODUCT_DELETED,
        INVENTORY_UPDATED,
        PRICE_UPDATED
    }

    public static class ProductEventPayload {
        private String title;
        private String description;
        private Double price;
        private String currency;
        private Integer availableQuantity;
        private String condition;
        private String status;
        private String categoryId;
        private String categoryName;
        private String brandName;
        private String sellerId;
        private String sellerNickname;
        private String sellerReputation;
        private java.util.Map<String, Object> attributes;
        private Integer totalSold;
        private Integer viewCount;
        private Double conversionRate;
        private Double averageRating;
        private Integer reviewCount;
        private Instant createdAt;

        // Getters e Setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }

        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }

        public Integer getAvailableQuantity() { return availableQuantity; }
        public void setAvailableQuantity(Integer availableQuantity) { this.availableQuantity = availableQuantity; }

        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getCategoryId() { return categoryId; }
        public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

        public String getBrandName() { return brandName; }
        public void setBrandName(String brandName) { this.brandName = brandName; }

        public String getSellerId() { return sellerId; }
        public void setSellerId(String sellerId) { this.sellerId = sellerId; }

        public String getSellerNickname() { return sellerNickname; }
        public void setSellerNickname(String sellerNickname) { this.sellerNickname = sellerNickname; }

        public String getSellerReputation() { return sellerReputation; }
        public void setSellerReputation(String sellerReputation) { this.sellerReputation = sellerReputation; }

        public java.util.Map<String, Object> getAttributes() { return attributes; }
        public void setAttributes(java.util.Map<String, Object> attributes) { this.attributes = attributes; }

        public Integer getTotalSold() { return totalSold; }
        public void setTotalSold(Integer totalSold) { this.totalSold = totalSold; }

        public Integer getViewCount() { return viewCount; }
        public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }

        public Double getConversionRate() { return conversionRate; }
        public void setConversionRate(Double conversionRate) { this.conversionRate = conversionRate; }

        public Double getAverageRating() { return averageRating; }
        public void setAverageRating(Double averageRating) { this.averageRating = averageRating; }

        public Integer getReviewCount() { return reviewCount; }
        public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }

        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    }
}