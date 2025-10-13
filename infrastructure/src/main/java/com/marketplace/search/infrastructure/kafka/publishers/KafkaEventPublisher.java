package com.marketplace.search.infrastructure.kafka.publishers;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.search.domain.events.DomainEvent;
import com.marketplace.search.domain.entities.Product;
import com.marketplace.search.domain.events.ProductCreatedEvent;
import com.marketplace.search.domain.events.ProductEvent;
import com.marketplace.search.domain.events.ProductUpdatedEvent;
import com.marketplace.search.domain.repositories.EventPublisher;
import com.marketplace.search.domain.valueobjects.ProductInfo;
import com.marketplace.search.domain.valueobjects.ProductMetrics;
import com.marketplace.search.domain.valueobjects.ProductStatus;
import com.marketplace.search.domain.valueobjects.Seller;
import com.marketplace.search.domain.valueobjects.SellerReputation;

/**
 * Implementação do publicador de eventos usando Apache Kafka
 */
@Component
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String productEventsTopic;
    private final String eventsSource;

    public KafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${kafka.topics.product-events:product-events}") String productEventsTopic,
            @Value("${kafka.events.source:search-api}") String eventsSource) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.productEventsTopic = productEventsTopic;
        this.eventsSource = eventsSource;
    }

    @Override
    public void publish(DomainEvent event) {
        try {
            String topic = determineTopicForEvent(event);
            String key = extractKeyFromEvent(event);
            String message = serializeEvent(event);

            CompletableFuture<SendResult<String, String>> future = 
                kafkaTemplate.send(topic, key, message);

            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to publish event {} to topic {}: {}", 
                        event.getClass().getSimpleName(), topic, throwable.getMessage());
                } else {
                    logger.debug("Successfully published event {} to topic {} with offset: {}", 
                        event.getClass().getSimpleName(), topic, 
                        result.getRecordMetadata().offset());
                }
            });

        } catch (Exception e) {
            logger.error("Error publishing event {}: {}", 
                event.getClass().getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }

    @Override
    public void publishAsync(DomainEvent event) {
        // O comportamento padrão já é assíncrono
        publish(event);
    }

    @Override
    public void publishBatch(java.util.List<DomainEvent> events) {
        for (DomainEvent event : events) {
            publish(event);
        }
    }

    /**
     * Determina o tópico baseado no tipo do evento
     */
    private String determineTopicForEvent(DomainEvent event) {
        if (event instanceof ProductEvent) {
            return productEventsTopic;
        }
        
        // Tópico padrão para eventos não mapeados
        return "domain-events";
    }

    /**
     * Extrai a chave para particionamento baseada no evento
     */
    private String extractKeyFromEvent(DomainEvent event) {
        if (event instanceof ProductEvent productEvent) {
            return productEvent.getProductId().getValue();
        }
        
        return event.getAggregateId();
    }

    /**
     * Serializa o evento para JSON
     */
    private String serializeEvent(DomainEvent event) {
        try {
            if (event instanceof ProductCreatedEvent createdEvent) {
                ProductEventMessage message = buildProductMessage(createdEvent.getProduct(),
                    ProductEventType.PRODUCT_CREATED, createdEvent.getOccurredOn());
                return objectMapper.writeValueAsString(message);
            }

            if (event instanceof ProductUpdatedEvent updatedEvent) {
                ProductEventMessage message = buildProductMessage(updatedEvent.getProduct(),
                    ProductEventType.PRODUCT_UPDATED, updatedEvent.getOccurredOn());
                return objectMapper.writeValueAsString(message);
            }

            return objectMapper.writeValueAsString(new EventWrapper(event));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize event: {}", event.getClass().getSimpleName(), e);
            throw new RuntimeException("Failed to serialize event", e);
        }
    }

    private ProductEventMessage buildProductMessage(Product product, ProductEventType eventType, Instant occurredOn) {
        ProductInfo info = product.getInfo();
        ProductMetrics metrics = product.getMetrics();
        ProductStatus status = product.getStatus();
        Seller seller = product.getSeller();
        SellerReputation reputation = seller.getReputation();

        ProductEventPayload payload = new ProductEventPayload();
        payload.setTitle(info.getTitle());
        payload.setDescription(info.getDescription());
        payload.setPrice(info.getPrice().doubleValue());
        payload.setCurrency(info.getCurrency());
        payload.setAvailableQuantity(metrics.getStockQuantity());
        payload.setCondition(product.getInfo().getAttributes().stream()
            .filter(attr -> attr.toLowerCase().contains("used"))
            .findFirst()
            .map(attr -> "USED")
            .orElse("NEW"));
        payload.setStatus(status.isActive() ? "ACTIVE" : "INACTIVE");
        payload.setCategoryId(info.getCategory().getId());
        payload.setCategoryName(info.getCategory().getName());
        payload.setCategoryPath(info.getCategory().getPath());
        payload.setBrandId(info.getBrand().getId());
        payload.setBrandName(info.getBrand().getName());
        payload.setBrandDescription(info.getBrand().getDescription());
        payload.setSellerId(seller.getId());
        payload.setSellerNickname(seller.getName());
        payload.setSellerType(seller.getType().name());
        payload.setSellerStatus(seller.getStatus().name());
        payload.setSellerScore(reputation.getScore());
        payload.setSellerTotalReviews(reputation.getTotalReviews());
        payload.setSellerCancellationRate(reputation.getCancellationRate());
        payload.setSellerDeliveryPerformance(reputation.getDeliveryPerformance());
        payload.setTotalSold(metrics.getTotalSales());
        payload.setViewCount(metrics.getTotalViews());
        payload.setConversionRate(metrics.getConversionRate());
        payload.setAverageRating(metrics.getAverageRating());
        payload.setReviewCount(metrics.getTotalReviews());
        payload.setCreatedAt(product.getCreatedAt());

        Map<String, Object> attributeMap = new HashMap<>();
        info.getAttributes().forEach(attr -> attributeMap.put(attr, true));
        payload.setAttributes(attributeMap);

        return new ProductEventMessage(
            product.getId().getValue(),
            eventType,
            payload,
            occurredOn,
            eventsSource
        );
    }

    private enum ProductEventType {
        PRODUCT_CREATED,
        PRODUCT_UPDATED,
        PRODUCT_DELETED,
        INVENTORY_UPDATED,
        PRICE_UPDATED
    }

    /**
     * Wrapper para eventos que inclui metadados
     */
    public static class EventWrapper {
        private final String eventType;
        private final String eventId;
        private final String aggregateId;
        private final long timestamp;
        private final DomainEvent payload;

        public EventWrapper(DomainEvent event) {
            this.eventType = event.getClass().getSimpleName();
            this.eventId = event.getEventId();
            this.aggregateId = event.getAggregateId();
            this.timestamp = event.getOccurredOn().toEpochMilli();
            this.payload = event;
        }

        public String getEventType() {
            return eventType;
        }

        public String getEventId() {
            return eventId;
        }

        public String getAggregateId() {
            return aggregateId;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public DomainEvent getPayload() {
            return payload;
        }
    }

    /**
     * Mensagem enviada para o tópico de produtos.
     */
    public static class ProductEventMessage {
        private final String productId;
        private final ProductEventType eventType;
        private final ProductEventPayload payload;
        private final Instant timestamp;
        private final String source;

        public ProductEventMessage(String productId, ProductEventType eventType,
                                   ProductEventPayload payload, Instant timestamp,
                                   String source) {
            this.productId = productId;
            this.eventType = eventType;
            this.payload = payload;
            this.timestamp = timestamp;
            this.source = source;
        }

        public String getProductId() {
            return productId;
        }

        public ProductEventType getEventType() {
            return eventType;
        }

        public ProductEventPayload getPayload() {
            return payload;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public String getSource() {
            return source;
        }
    }

    /**
     * Payload com os dados relevantes do produto.
     */
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
        private String categoryPath;
        private String brandId;
        private String brandName;
        private String brandDescription;
        private String sellerId;
        private String sellerNickname;
        private String sellerType;
        private String sellerStatus;
        private Double sellerScore;
        private Integer sellerTotalReviews;
        private Double sellerCancellationRate;
        private Double sellerDeliveryPerformance;
        private Map<String, Object> attributes;
        private Integer totalSold;
        private Integer viewCount;
        private Double conversionRate;
        private Double averageRating;
        private Integer reviewCount;
        private Instant createdAt;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Double getPrice() {
            return price;
        }

        public void setPrice(Double price) {
            this.price = price;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public Integer getAvailableQuantity() {
            return availableQuantity;
        }

        public void setAvailableQuantity(Integer availableQuantity) {
            this.availableQuantity = availableQuantity;
        }

        public String getCondition() {
            return condition;
        }

        public void setCondition(String condition) {
            this.condition = condition;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(String categoryId) {
            this.categoryId = categoryId;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public void setCategoryName(String categoryName) {
            this.categoryName = categoryName;
        }

        public String getCategoryPath() {
            return categoryPath;
        }

        public void setCategoryPath(String categoryPath) {
            this.categoryPath = categoryPath;
        }

        public String getBrandId() {
            return brandId;
        }

        public void setBrandId(String brandId) {
            this.brandId = brandId;
        }

        public String getBrandName() {
            return brandName;
        }

        public void setBrandName(String brandName) {
            this.brandName = brandName;
        }

        public String getBrandDescription() {
            return brandDescription;
        }

        public void setBrandDescription(String brandDescription) {
            this.brandDescription = brandDescription;
        }

        public String getSellerId() {
            return sellerId;
        }

        public void setSellerId(String sellerId) {
            this.sellerId = sellerId;
        }

        public String getSellerNickname() {
            return sellerNickname;
        }

        public void setSellerNickname(String sellerNickname) {
            this.sellerNickname = sellerNickname;
        }

        public String getSellerType() {
            return sellerType;
        }

        public void setSellerType(String sellerType) {
            this.sellerType = sellerType;
        }

        public String getSellerStatus() {
            return sellerStatus;
        }

        public void setSellerStatus(String sellerStatus) {
            this.sellerStatus = sellerStatus;
        }

        public Double getSellerScore() {
            return sellerScore;
        }

        public void setSellerScore(Double sellerScore) {
            this.sellerScore = sellerScore;
        }

        public Integer getSellerTotalReviews() {
            return sellerTotalReviews;
        }

        public void setSellerTotalReviews(Integer sellerTotalReviews) {
            this.sellerTotalReviews = sellerTotalReviews;
        }

        public Double getSellerCancellationRate() {
            return sellerCancellationRate;
        }

        public void setSellerCancellationRate(Double sellerCancellationRate) {
            this.sellerCancellationRate = sellerCancellationRate;
        }

        public Double getSellerDeliveryPerformance() {
            return sellerDeliveryPerformance;
        }

        public void setSellerDeliveryPerformance(Double sellerDeliveryPerformance) {
            this.sellerDeliveryPerformance = sellerDeliveryPerformance;
        }

        public Map<String, Object> getAttributes() {
            return attributes;
        }

        public void setAttributes(Map<String, Object> attributes) {
            this.attributes = attributes;
        }

        public Integer getTotalSold() {
            return totalSold;
        }

        public void setTotalSold(Integer totalSold) {
            this.totalSold = totalSold;
        }

        public Integer getViewCount() {
            return viewCount;
        }

        public void setViewCount(Integer viewCount) {
            this.viewCount = viewCount;
        }

        public Double getConversionRate() {
            return conversionRate;
        }

        public void setConversionRate(Double conversionRate) {
            this.conversionRate = conversionRate;
        }

        public Double getAverageRating() {
            return averageRating;
        }

        public void setAverageRating(Double averageRating) {
            this.averageRating = averageRating;
        }

        public Integer getReviewCount() {
            return reviewCount;
        }

        public void setReviewCount(Integer reviewCount) {
            this.reviewCount = reviewCount;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }
    }
}