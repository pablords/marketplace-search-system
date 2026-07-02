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
import com.marketplace.search.indexing.application.handlers.payloads.ProductMetricsPayload;
import com.marketplace.search.indexing.application.services.ProductMetricsCacheService;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ProductMetricsEventHandler {
  private static final Logger logger = LoggerFactory.getLogger(ProductMetricsEventHandler.class);
  private final ProductMetricsCacheService metricsCacheService;
  private final ObjectMapper objectMapper;

  public ProductMetricsEventHandler(ProductMetricsCacheService metricsCacheService, ObjectMapper objectMapper) {
    this.metricsCacheService = metricsCacheService;
    if (objectMapper == null) {
      ObjectMapper om = new ObjectMapper();
      om.registerModule(new JavaTimeModule());
      om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
      this.objectMapper = om;
    } else {
      this.objectMapper = objectMapper;
    }
  }

  @KafkaListener(topics = "${kafka.topics.product-metrics-events:catalog-db.public.product_metrics}", groupId = "${kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
  public void handleProductMetricsEvent(
      @Payload GenericRecord message,
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
      @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
      ConsumerRecord<String, GenericRecord> record,
      Acknowledgment acknowledgment) {

    Span currentSpan = Span.current();

    try {
      logger.debug("Recebido evento CDC de ProductMetrics - Topic: {}, Partition: {}, Offset: {}",
          topic, partition, record.offset());

      DebeziumCDCEvent cdcEvent = AvroCDCEventConverter.convert(message);

      // Converter o payload para ProductMetricsPayload
      ProductMetricsPayload metricsAfter = objectMapper.convertValue(cdcEvent.getAfter(), ProductMetricsPayload.class);
      ProductMetricsPayload metricsBefore = cdcEvent.getBefore() != null 
          ? objectMapper.convertValue(cdcEvent.getBefore(), ProductMetricsPayload.class) 
          : null;

      if (metricsAfter != null) {
        currentSpan.setAttribute("product.id", metricsAfter.getProductId());
      } else if (metricsBefore != null) {
        currentSpan.setAttribute("product.id", metricsBefore.getProductId());
      }

      switch (cdcEvent.getOperation()) {
        case "c", "r", "u" -> {
          // create, read (snapshot), update
          if (metricsAfter != null) {
            metricsCacheService.cacheMetrics(metricsAfter);
            logger.info("ProductMetrics atualizadas no cache: productId={}", metricsAfter.getProductId());
          }
        }
        case "d" -> {
          // delete
          if (metricsBefore != null) {
            metricsCacheService.evictMetrics(metricsBefore.getProductId());
            logger.info("ProductMetrics removidas do cache: productId={}", metricsBefore.getProductId());
          }
        }
        default -> {
          logger.warn("Operação CDC não suportada para ProductMetrics: {}", cdcEvent.getOperation());
        }
      }

      acknowledgment.acknowledge();

    } catch (Exception e) {
      logger.error("Erro ao processar evento CDC de ProductMetrics - Message: {}, Error: {}", message, e.getMessage(), e);
      
      // Mark span as error
      currentSpan.setStatus(StatusCode.ERROR, "ProductMetrics Process Error: " + e.getMessage());
      currentSpan.setAttribute("error", true);
      currentSpan.recordException(e);
      
      // Em caso de erro, fazer acknowledge para evitar loop infinito
      acknowledgment.acknowledge();
    }
  }
}
