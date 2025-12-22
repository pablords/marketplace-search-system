package com.marketplace.search.indexing.application.handlers;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.lang.Nullable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.marketplace.search.indexing.application.commands.ProductCommand;
import com.marketplace.search.indexing.application.events.DebeziumCDCEvent;
import com.marketplace.search.indexing.application.handlers.payloads.ProductPayload;
import com.marketplace.search.indexing.application.mappers.ProductMapper;
import com.marketplace.search.indexing.application.usecases.IndexProductUseCase;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ProductEventHandler {
  private static final Logger logger = LoggerFactory.getLogger(ProductEventHandler.class);
  private final ProductMapper productMapper;
  private final IndexProductUseCase indexProductUseCase;
  private final ObjectMapper objectMapper;

  public ProductEventHandler(IndexProductUseCase indexProductUseCase, ProductMapper productMapper,
      @Nullable ObjectMapper objectMapper) {
    this.indexProductUseCase = indexProductUseCase;
    this.productMapper = productMapper;

    if (objectMapper == null) {
      ObjectMapper om = new ObjectMapper();
      om.registerModule(new JavaTimeModule());
      om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
      this.objectMapper = om;
    } else {
      this.objectMapper = objectMapper;
    }
  }

  @KafkaListener(topics = "${kafka.topics.product-events}", groupId = "${kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
  public void handleProductEvent(
      @Payload String message,
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
      @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
      @Header(value = "eventType", required = false) String eventType,
      ConsumerRecord<String, String> record,
      Acknowledgment acknowledgment) {

    try {
      logger.info("Recebido evento CDC - Topic: {}, Partition: {}, Offset: {}, Timestamp: {}",
          topic, partition, record.offset(), Instant.ofEpochMilli(timestamp));

      DebeziumCDCEvent cdcEvent = objectMapper.readValue(message, DebeziumCDCEvent.class);

      logger.info("Operação CDC: {}, Table: {}", cdcEvent.getOperation(),
          cdcEvent.getSource() != null ? cdcEvent.getSource().getTable() : "unknown");

      CompletableFuture<Void> processingFuture = switch (cdcEvent.getOperation()) {
        case "c", "r", "u" -> processProductUpsert(cdcEvent); // create, read (snapshot), update
        case "d" -> processProductDeletion(cdcEvent); // delete
        default -> {
          logger.warn("Operação CDC não suportada: {}", cdcEvent.getOperation());
          yield CompletableFuture.completedFuture(null);
        }
      };

      processingFuture
          .thenRun(() -> {
            logger.info("Evento CDC processado com sucesso - Operation: {}", cdcEvent.getOperation());
            acknowledgment.acknowledge();
          })
          .exceptionally(throwable -> {
            logger.error("Erro ao processar evento CDC - Operation: {}, Error: {}",
                cdcEvent.getOperation(), throwable.getMessage(), throwable);
            // Em caso de erro, não fazer acknowledge para reprocessar a mensagem
            return null;
          });

    } catch (Exception e) {
      logger.error("Erro ao parsear evento CDC do Kafka - Message: {}, Error: {}", message, e.getMessage(), e);
      // Em caso de erro de parsing, fazer acknowledge para evitar loop infinito
      acknowledgment.acknowledge();
    }
  }

  private CompletableFuture<Void> processProductUpsert(DebeziumCDCEvent cdcEvent) {
    ProductPayload productData = cdcEvent.getAfter();
    if (productData == null) {
      logger.warn("Evento CDC sem dados 'after', ignorando");
      return CompletableFuture.completedFuture(null);
    }

    logger.debug("Processando criação/atualização do produto: {}", productData.getId());

    ProductCommand productCommand = productMapper.mapProductPayloadToDTO(productData);
    indexProductUseCase.executeAsync(productCommand);
    return CompletableFuture.completedFuture(null);
  }

  private CompletableFuture<Void> processProductDeletion(DebeziumCDCEvent cdcEvent) {
    ProductPayload productData = cdcEvent.getBefore();
    if (productData == null) {
      logger.warn("Evento CDC de deleção sem dados 'before', ignorando");
      return CompletableFuture.completedFuture(null);
    }

    logger.debug("Processando deleção do produto: {}", productData.getId());

    // TODO: Implementar remoção do índice
    // return deleteProductUseCase.execute(productData.getId());
    return CompletableFuture.completedFuture(null);
  }

}
