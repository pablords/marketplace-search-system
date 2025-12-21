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
import com.marketplace.search.indexing.application.handlers.payloads.BrandPayload;
import com.marketplace.search.indexing.application.services.DimensionCacheService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class BrandEventHandler {
  private static final Logger logger = LoggerFactory.getLogger(BrandEventHandler.class);
  private final DimensionCacheService dimensionCacheService;
  private final ObjectMapper objectMapper;

  public BrandEventHandler(DimensionCacheService dimensionCacheService, ObjectMapper objectMapper) {
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

  @KafkaListener(topics = "${kafka.topics.brand-events:catalog-db.public.brands}", groupId = "${kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
  public void handleBrandEvent(
      @Payload String message,
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
      @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
      ConsumerRecord<String, String> record,
      Acknowledgment acknowledgment) {

    try {
      logger.debug("Recebido evento CDC de Brand - Topic: {}, Partition: {}, Offset: {}",
          topic, partition, record.offset());

      DebeziumCDCEvent cdcEvent = objectMapper.readValue(message, DebeziumCDCEvent.class);

      // Converter o payload para BrandPayload
      BrandPayload brandAfter = objectMapper.convertValue(cdcEvent.getAfter(), BrandPayload.class);
      BrandPayload brandBefore = cdcEvent.getBefore() != null 
          ? objectMapper.convertValue(cdcEvent.getBefore(), BrandPayload.class) 
          : null;

      switch (cdcEvent.getOperation()) {
        case "c", "r", "u" -> {
          // create, read (snapshot), update
          if (brandAfter != null) {
            dimensionCacheService.cacheBrand(brandAfter);
            logger.info("Brand atualizado no cache: {}", brandAfter.getId());
          }
        }
        case "d" -> {
          // delete
          if (brandBefore != null) {
            dimensionCacheService.evictBrand(brandBefore.getId());
            logger.info("Brand removido do cache: {}", brandBefore.getId());
          }
        }
        default -> {
          logger.warn("Operação CDC não suportada para Brand: {}", cdcEvent.getOperation());
        }
      }

      acknowledgment.acknowledge();

    } catch (Exception e) {
      logger.error("Erro ao processar evento CDC de Brand - Message: {}, Error: {}", message, e.getMessage(), e);
      // Em caso de erro, fazer acknowledge para evitar loop infinito
      acknowledgment.acknowledge();
    }
  }
}

