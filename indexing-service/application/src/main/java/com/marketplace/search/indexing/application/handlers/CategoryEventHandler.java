package com.marketplace.search.indexing.application.handlers;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.marketplace.search.indexing.application.events.DebeziumCDCEvent;
import com.marketplace.search.indexing.application.handlers.payloads.CategoryPayload;
import com.marketplace.search.indexing.application.services.DimensionCacheService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CategoryEventHandler {
  private static final Logger logger = LoggerFactory.getLogger(CategoryEventHandler.class);
  private final DimensionCacheService dimensionCacheService;
  private final ObjectMapper objectMapper;

  public CategoryEventHandler(DimensionCacheService dimensionCacheService, ObjectMapper objectMapper) {
    this.dimensionCacheService = dimensionCacheService;
    if (objectMapper == null) {
      ObjectMapper om = new ObjectMapper();
      om.registerModule(new JavaTimeModule());
      om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
      this.objectMapper = om;
    } else {
      this.objectMapper = objectMapper;
    }
  }

  @KafkaListener(topics = "${kafka.topics.category-events:catalog-db.public.categories}", groupId = "${kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
  public void handleCategoryEvent(
      @Payload String message,
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
      @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
      ConsumerRecord<String, String> record,
      Acknowledgment acknowledgment) {

    try {
      logger.debug("Recebido evento CDC de Category - Topic: {}, Partition: {}, Offset: {}",
          topic, partition, record.offset());

      DebeziumCDCEvent cdcEvent = objectMapper.readValue(message, DebeziumCDCEvent.class);

      // Converter o payload para CategoryPayload
      CategoryPayload categoryAfter = objectMapper.convertValue(cdcEvent.getAfter(), CategoryPayload.class);
      CategoryPayload categoryBefore = cdcEvent.getBefore() != null 
          ? objectMapper.convertValue(cdcEvent.getBefore(), CategoryPayload.class) 
          : null;

      switch (cdcEvent.getOperation()) {
        case "c", "r", "u" -> {
          // create, read (snapshot), update
          if (categoryAfter != null) {
            dimensionCacheService.cacheCategory(categoryAfter);
            logger.info("Category atualizada no cache: {}", categoryAfter.getId());
          }
        }
        case "d" -> {
          // delete
          if (categoryBefore != null) {
            dimensionCacheService.evictCategory(categoryBefore.getId());
            logger.info("Category removida do cache: {}", categoryBefore.getId());
          }
        }
        default -> {
          logger.warn("Operação CDC não suportada para Category: {}", cdcEvent.getOperation());
        }
      }

      acknowledgment.acknowledge();

    } catch (Exception e) {
      logger.error("Erro ao processar evento CDC de Category - Message: {}, Error: {}", message, e.getMessage(), e);
      // Em caso de erro, fazer acknowledge para evitar loop infinito
      acknowledgment.acknowledge();
    }
  }
}

