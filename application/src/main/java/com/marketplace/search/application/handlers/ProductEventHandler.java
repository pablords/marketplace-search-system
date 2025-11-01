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
import com.marketplace.search.application.dto.BrandDTO;
import com.marketplace.search.application.dto.CategoryDTO;
import com.marketplace.search.application.dto.ProductDTO;
import com.marketplace.search.application.dto.SellerDTO;
import com.marketplace.search.application.usecases.IndexProductUseCase;

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

      DebeziumCdcEvent cdcEvent = objectMapper.readValue(message, DebeziumCdcEvent.class);
      
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

  private CompletableFuture<Void> processProductUpsert(DebeziumCdcEvent cdcEvent) {
    ProductData productData = cdcEvent.getAfter();
    if (productData == null) {
      logger.warn("Evento CDC sem dados 'after', ignorando");
      return CompletableFuture.completedFuture(null);
    }

    logger.debug("Processando criação/atualização do produto: {}", productData.getId());

    ProductDTO productDTO = mapProductDataToDTO(productData);
    indexProductUseCase.executeAsync(productDTO);
    return CompletableFuture.completedFuture(null);
  }

  private CompletableFuture<Void> processProductDeletion(DebeziumCdcEvent cdcEvent) {
    ProductData productData = cdcEvent.getBefore();
    if (productData == null) {
      logger.warn("Evento CDC de deleção sem dados 'before', ignorando");
      return CompletableFuture.completedFuture(null);
    }

    logger.debug("Processando deleção do produto: {}", productData.getId());

    // TODO: Implementar remoção do índice
    // return deleteProductUseCase.execute(productData.getId());
    return CompletableFuture.completedFuture(null);
  }

  private ProductDTO mapProductDataToDTO(ProductData data) {
    ProductDTO dto = new ProductDTO();
    dto.setId(data.getId());
    dto.setTitle(data.getTitle());
    dto.setDescription(data.getDescription());
    dto.setPrice(data.getPrice() != null ? new BigDecimal(data.getPrice()) : null);
    dto.setCurrency(data.getCurrency());
    dto.setStockQuantity(data.getAvailableQuantity());
    dto.setCondition(data.getCondition());
    dto.setIsActive("ACTIVE".equals(data.getStatus()));
    
    // Category
    CategoryDTO category = new CategoryDTO();
    category.setId(data.getCategoryId());
    category.setName(data.getCategoryName());
    category.setPath(data.getCategoryPath());
    dto.setCategory(category);
    
    // Brand
    BrandDTO brand = new BrandDTO();
    brand.setId(data.getBrandId());
    brand.setName(data.getBrandName());
    dto.setBrand(brand);
    
    // Seller
    SellerDTO seller = new SellerDTO();
    seller.setId(data.getSellerId());
    seller.setName(data.getSellerName());
    dto.setSeller(seller);
    
    return dto;
  }

}
