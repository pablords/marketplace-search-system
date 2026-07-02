package com.marketplace.search.indexing.application.handlers;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.avro.generic.GenericRecord;
import com.marketplace.search.indexing.application.events.AvroCDCEventConverter;
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
import com.marketplace.search.indexing.application.handlers.payloads.SellerPayload;
import com.marketplace.search.indexing.application.services.DimensionCacheService;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SellerEventHandler {
  private static final Logger logger = LoggerFactory.getLogger(SellerEventHandler.class);
  private final DimensionCacheService dimensionCacheService;
  private final ObjectMapper objectMapper;

  public SellerEventHandler(DimensionCacheService dimensionCacheService, ObjectMapper objectMapper) {
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

  @KafkaListener(topics = "${kafka.topics.seller-events:catalog-db.public.sellers}", groupId = "${kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
  public void handleSellerEvent(
      @Payload GenericRecord message,
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
      @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
      ConsumerRecord<String, GenericRecord> record,
      Acknowledgment acknowledgment) {

    Span currentSpan = Span.current();

    try {
      logger.debug("Recebido evento CDC de Seller - Topic: {}, Partition: {}, Offset: {}",
          topic, partition, record.offset());

      DebeziumCDCEvent cdcEvent = AvroCDCEventConverter.convert(message);

      // Converter o payload para SellerPayload
      SellerPayload sellerAfter = objectMapper.convertValue(cdcEvent.getAfter(), SellerPayload.class);
      SellerPayload sellerBefore = cdcEvent.getBefore() != null 
          ? objectMapper.convertValue(cdcEvent.getBefore(), SellerPayload.class) 
          : null;

      if (sellerAfter != null) {
        currentSpan.setAttribute("seller.id", sellerAfter.getId());
      } else if (sellerBefore != null) {
        currentSpan.setAttribute("seller.id", sellerBefore.getId());
      }

      switch (cdcEvent.getOperation()) {
        case "c", "r", "u" -> {
          // create, read (snapshot), update
          if (sellerAfter != null) {
            dimensionCacheService.cacheSeller(sellerAfter);
            logger.info("Seller atualizado no cache: {}", sellerAfter.getId());
          }
        }
        case "d" -> {
          // delete
          if (sellerBefore != null) {
            dimensionCacheService.evictSeller(sellerBefore.getId());
            logger.info("Seller removido do cache: {}", sellerBefore.getId());
          }
        }
        default -> {
          logger.warn("Operação CDC não suportada para Seller: {}", cdcEvent.getOperation());
        }
      }

      acknowledgment.acknowledge();

    } catch (Exception e) {
      logger.error("Erro ao processar evento CDC de Seller - Message: {}, Error: {}", message, e.getMessage(), e);
      
      // Mark span as error
      currentSpan.setStatus(StatusCode.ERROR, "Seller Process Error: " + e.getMessage());
      currentSpan.setAttribute("error", true);
      currentSpan.recordException(e);
      
      // Em caso de erro, fazer acknowledge para evitar loop infinito
      acknowledgment.acknowledge();
    }
  }
}
