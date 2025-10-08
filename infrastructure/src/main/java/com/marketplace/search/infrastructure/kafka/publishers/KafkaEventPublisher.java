package com.marketplace.search.infrastructure.kafka.publishers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.search.domain.events.DomainEvent;
import com.marketplace.search.domain.events.ProductEvent;
import com.marketplace.search.domain.repositories.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Implementação do publicador de eventos usando Apache Kafka
 */
@Component
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String productEventsTopic;

    public KafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${kafka.topics.product-events:product-events}") String productEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.productEventsTopic = productEventsTopic;
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
            return productEvent.getProductId();
        }
        
        return event.getAggregateId();
    }

    /**
     * Serializa o evento para JSON
     */
    private String serializeEvent(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(new EventWrapper(event));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize event: {}", event.getClass().getSimpleName(), e);
            throw new RuntimeException("Failed to serialize event", e);
        }
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
}