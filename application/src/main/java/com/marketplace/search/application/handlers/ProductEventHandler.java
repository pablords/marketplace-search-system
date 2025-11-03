package com.marketplace.search.application.handlers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.search.application.events.DebeziumCDCEvent;
import com.marketplace.search.application.handlers.payloads.ProductPayload;
import com.marketplace.search.application.usecases.IndexProductUseCase;
import com.marketplace.search.interfaces.rest.dtos.BrandDTO;
import com.marketplace.search.interfaces.rest.dtos.CategoryDTO;
import com.marketplace.search.interfaces.rest.dtos.ProductDTO;
import com.marketplace.search.interfaces.rest.dtos.SellerDTO;

@Component
public class ProductEventHandler {
  private static final Logger logger = LoggerFactory.getLogger(ProductEventHandler.class);

  private final IndexProductUseCase indexProductUseCase;
  private final ObjectMapper objectMapper;

  @Autowired
  public ProductEventHandler(IndexProductUseCase indexProductUseCase, ObjectMapper objectMapper) {
    this.indexProductUseCase = indexProductUseCase;
    this.objectMapper = objectMapper;
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

    ProductDTO productDTO = mapProductDataToDTO(productData);
    indexProductUseCase.executeAsync(productDTO);
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

  private ProductDTO mapProductDataToDTO(ProductPayload data) {
    // Category
    CategoryDTO category = new CategoryDTO(
        data.getCategoryId(), 
        data.getCategoryName(), 
        null, 
        data.getCategoryPath()
    );
    
    // Brand
    BrandDTO brand = new BrandDTO(
        data.getBrandId(), 
        data.getBrandName(), 
        data.getDescription()
    );
    
    // Seller
    SellerDTO seller = SellerDTO.builder()
        .id(data.getSellerId())
        .name(data.getSellerName())
        .build();
    
    // Product usando builder
    return ProductDTO.builder()
        .id(data.getId())
        .title(data.getTitle())
        .description(data.getDescription())
        .price(data.getPrice() != null ? new BigDecimal(data.getPrice()) : null)
        .currency(data.getCurrency())
        .category(category)
        .brand(brand)
        .seller(seller)
        .stockQuantity(data.getAvailableQuantity())
        .condition(data.getCondition())
        .isActive("ACTIVE".equals(data.getStatus()))
        .build();
  }

}
