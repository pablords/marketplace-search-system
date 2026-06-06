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
import com.marketplace.search.indexing.application.services.EventDeduplicationService;
import com.marketplace.search.indexing.application.services.ProductEnrichmentService;
import com.marketplace.search.indexing.application.usecases.IndexProductUseCase;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ProductEventHandler {
  private static final Logger logger = LoggerFactory.getLogger(ProductEventHandler.class);
  private final ProductMapper productMapper;
  private final IndexProductUseCase indexProductUseCase;
  private final ProductEnrichmentService enrichmentService;
  private final EventDeduplicationService deduplicationService;
  private final ObjectMapper objectMapper;

  public ProductEventHandler(IndexProductUseCase indexProductUseCase, ProductMapper productMapper,
      ProductEnrichmentService enrichmentService, EventDeduplicationService deduplicationService,
      @Nullable ObjectMapper objectMapper) {
    this.indexProductUseCase = indexProductUseCase;
    this.productMapper = productMapper;
    this.enrichmentService = enrichmentService;
    this.deduplicationService = deduplicationService;

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

    Span currentSpan = Span.current();

    try {
      logger.info("Recebido evento CDC - Topic: {}, Partition: {}, Offset: {}, Timestamp: {}",
          topic, partition, record.offset(), Instant.ofEpochMilli(timestamp));

      DebeziumCDCEvent cdcEvent = objectMapper.readValue(message, DebeziumCDCEvent.class);

      logger.info("Operação CDC: {}, Table: {}", cdcEvent.getOperation(),
          cdcEvent.getSource() != null ? cdcEvent.getSource().getTable() : "unknown");

      // Verificar idempotência: extrair productId do evento antes de processar
      String productId = extractProductId(cdcEvent);
      Long eventTimestamp = cdcEvent.getTimestamp();
      Long kafkaOffset = record.offset();

      if (productId != null) {
        currentSpan.setAttribute("product.id", productId);
      }

      // Verificar se o evento já foi processado (deduplicação)
      if (productId != null && eventTimestamp != null && kafkaOffset != null) {
        if (deduplicationService.isDuplicate(productId, eventTimestamp, kafkaOffset)) {
          logger.warn("Evento duplicado ignorado - ProductId: {}, Timestamp: {}, Offset: {}. " +
              "Evento já foi processado anteriormente.", productId, eventTimestamp, kafkaOffset);
          // Acknowledge o evento duplicado para não reprocessar
          acknowledgment.acknowledge();
          return;
        }
      } else {
        logger.warn("Não foi possível extrair informações para deduplicação - ProductId: {}, " +
            "Timestamp: {}, Offset: {}. Continuando processamento sem verificação de duplicação.",
            productId, eventTimestamp, kafkaOffset);
      }

      // Determina qual pipeline assíncrona executar
      CompletableFuture<Void> processingFuture = switch (cdcEvent.getOperation()) {
        case "c", "r", "u" -> processProductUpsert(cdcEvent);
        case "d" -> processProductDeletion(cdcEvent);
        default -> {
          logger.warn("Operação CDC não suportada: {}", cdcEvent.getOperation());
          yield CompletableFuture.completedFuture(null);
        }
      };

      // 2. O SEGREDO DO BACKPRESSURE COM COMMIT MANUAL:
      // Bloqueamos a thread do listener do Kafka até que a task assíncrona termine.
      // Isso impede o Kafka de fazer um novo poll() de mensagens enquanto o
      // Elastic/OpenSearch estiver processando.
      processingFuture.join();

      // 3. SEU COMMIT MANUAL CONTROLADO:
      // Só chega aqui se o .join() terminar com sucesso (sem exceptions)
      logger.info("Evento CDC processado com sucesso - Operation: {}", cdcEvent.getOperation());
      acknowledgment.acknowledge();
    } catch (Exception e) {
      logger.error("Erro ao parsear evento CDC do Kafka - Message: {}, Error: {}", message, e.getMessage(), e);

      // Mark span as error
      currentSpan.setStatus(StatusCode.ERROR, "Parsing Error: " + e.getMessage());
      currentSpan.setAttribute("error", true);
      currentSpan.recordException(e);

      // Em caso de erro de parsing, fazer acknowledge para evitar loop infinito
      acknowledgment.acknowledge();
    }
  }

  private CompletableFuture<Void> processProductUpsert(DebeziumCDCEvent cdcEvent) {
    try {
      // Converter o payload para ProductPayload
      ProductPayload productData = objectMapper.convertValue(cdcEvent.getAfter(), ProductPayload.class);
      if (productData == null) {
        logger.warn("Evento CDC sem dados 'after', ignorando");
        return CompletableFuture.completedFuture(null);
      }

      logger.debug("Processando criação/atualização do produto: {}", productData.getId());

      // Enriquecer o produto com dados de dimensões e métricas
      ProductPayload enrichedProduct = enrichmentService.enrich(productData);
      logger.debug("Produto enriquecido: {}", enrichedProduct.toString());
      // Verificar se dados críticos estão disponíveis
      if (!isProductEnrichmentComplete(enrichedProduct)) {
        logger.warn("Produto {} não totalmente enriquecido - alguns dados podem estar faltando. " +
            "Brand: {}, Category: {}, Seller: {}. Continuando com indexação (eventual consistency aceitável)",
            enrichedProduct.getId(),
            enrichedProduct.getBrandName() != null,
            enrichedProduct.getCategoryName() != null,
            enrichedProduct.getSellerName() != null);
        // Continuar mesmo assim - eventual consistency é aceitável para indexação
      }

      ProductCommand productCommand = productMapper.mapProductPayloadToDTO(enrichedProduct);
      logger.debug("Product Command Após Mapper: {}", productCommand.toString());
      return indexProductUseCase.executeAsync(productCommand);

    } catch (Exception e) {
      logger.error("Erro ao processar upsert de produto", e);
      return CompletableFuture.failedFuture(e);
    }
  }

  private String extractProductId(DebeziumCDCEvent cdcEvent) {
    try {
      // Para operações de criação/atualização, usar 'after'
      if (cdcEvent.getAfter() != null) {
        ProductPayload productData = objectMapper.convertValue(cdcEvent.getAfter(), ProductPayload.class);
        if (productData != null && productData.getId() != null) {
          return productData.getId();
        }
      }

      // Para operações de deleção, usar 'before'
      if (cdcEvent.getBefore() != null) {
        ProductPayload productData = objectMapper.convertValue(cdcEvent.getBefore(), ProductPayload.class);
        if (productData != null && productData.getId() != null) {
          return productData.getId();
        }
      }
    } catch (Exception e) {
      logger.debug("Erro ao extrair productId do evento CDC: {}", e.getMessage());
    }

    return null;
  }

  private boolean isProductEnrichmentComplete(ProductPayload product) {
    // Verificar se pelo menos os IDs estão presentes (mínimo necessário)
    boolean hasBasicData = product.getId() != null &&
        product.getTitle() != null &&
        product.getBrandId() != null &&
        product.getCategoryId() != null &&
        product.getSellerId() != null;

    // Verificar se dados enriquecidos estão presentes (ideal)
    boolean hasEnrichedData = product.getBrandName() != null &&
        product.getCategoryName() != null &&
        product.getSellerName() != null;

    return hasBasicData && hasEnrichedData;
  }

  private CompletableFuture<Void> processProductDeletion(DebeziumCDCEvent cdcEvent) {
    try {
      // Converter o payload para ProductPayload
      ProductPayload productData = objectMapper.convertValue(cdcEvent.getBefore(), ProductPayload.class);
      if (productData == null) {
        logger.warn("Evento CDC de deleção sem dados 'before', ignorando");
        return CompletableFuture.completedFuture(null);
      }

      logger.debug("Processando deleção do produto: {}", productData.getId());

      // TODO: Implementar remoção do índice quando DeleteProductUseCase estiver
      // disponível
      logger.info("Deleção de produto {} registrada - remoção do índice será implementada em breve",
          productData.getId());
      return CompletableFuture.completedFuture(null);

    } catch (Exception e) {
      logger.error("Erro ao processar deleção de produto", e);
      return CompletableFuture.failedFuture(e);
    }
  }

}
