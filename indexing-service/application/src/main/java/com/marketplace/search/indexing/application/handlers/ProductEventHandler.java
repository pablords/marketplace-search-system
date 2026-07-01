package com.marketplace.search.indexing.application.handlers;


import java.util.concurrent.CompletableFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.CompletionException;
import java.util.Objects;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.lang.Nullable;
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
  private final Executor executor;

  public ProductEventHandler(IndexProductUseCase indexProductUseCase, ProductMapper productMapper,
      ProductEnrichmentService enrichmentService, EventDeduplicationService deduplicationService,
      @Qualifier("applicationTaskExecutor") Executor executor,
      @Nullable ObjectMapper objectMapper) {
    this.indexProductUseCase = indexProductUseCase;
    this.productMapper = productMapper;
    this.enrichmentService = enrichmentService;
    this.deduplicationService = deduplicationService;
    this.executor = executor;

    if (objectMapper == null) {
      ObjectMapper om = new ObjectMapper();
      om.registerModule(new JavaTimeModule());
      om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
      this.objectMapper = om;
    } else {
      this.objectMapper = objectMapper;
    }
  }

  @KafkaListener(topics = "${kafka.topics.product-events}", groupId = "${kafka.consumer.group-id}", containerFactory = "batchKafkaListenerContainerFactory")
  public void handleProductEvent(
      List<ConsumerRecord<String, String>> records,
      Acknowledgment acknowledgment) {

    Span currentSpan = Span.current();

    if (records == null || records.isEmpty()) {
      return;
    }

    logger.info("Recebido lote com {} eventos CDC", records.size());
    List<ProductCommand> batchCommands = new ArrayList<>();
    Map<String, DebeziumCDCEvent> latestEventsPerProduct = new LinkedHashMap<>();

    // Fase 1: Parse e Deduplicação
    for (int i = 0; i < records.size(); i++) {
        ConsumerRecord<String, String> record = records.get(i);
        try {
            DebeziumCDCEvent cdcEvent = objectMapper.readValue(record.value(), DebeziumCDCEvent.class);
            String productId = extractProductId(cdcEvent);

            if (productId != null) {
                Long eventTimestamp = cdcEvent.getTimestamp();
                Long kafkaOffset = record.offset();

                if (eventTimestamp != null && kafkaOffset != null) {
                    if (deduplicationService.isDuplicate(productId, eventTimestamp, kafkaOffset)) {
                        logger.warn("Evento duplicado ignorado - ProductId: {}, Offset: {}", productId, kafkaOffset);
                        continue;
                    }
                }
                // Mantém apenas o evento mais recente do produto no lote
                latestEventsPerProduct.put(productId, cdcEvent);
            } else {
                logger.warn("ProductId não encontrado no evento CDC, offset: {}", record.offset());
            }
        } catch (Exception e) {
            logger.error("Erro ao parsear evento CDC no offset {}: {}", record.offset(), e.getMessage());
            currentSpan.setStatus(StatusCode.ERROR, "Parsing Error: " + e.getMessage());
            currentSpan.recordException(e);
            throw new org.springframework.kafka.listener.BatchListenerFailedException("Falha ao parsear registro do lote", e, record);
        }
    }

    // Fase 2: Enriquecimento e Mapeamento
    List<CompletableFuture<ProductCommand>> enrichmentFutures = latestEventsPerProduct.entrySet().stream()
        .map(entry -> CompletableFuture.supplyAsync(() -> {
            DebeziumCDCEvent cdcEvent = entry.getValue();
            try {
                if ("d".equals(cdcEvent.getOperation())) {
                    processProductDeletion(cdcEvent).join();
                    return null;
                } else if ("c".equals(cdcEvent.getOperation()) || "r".equals(cdcEvent.getOperation()) || "u".equals(cdcEvent.getOperation())) {
                    return processProductUpsertSync(cdcEvent);
                } else {
                    logger.warn("Operação CDC não suportada: {}", cdcEvent.getOperation());
                    return null;
                }
            } catch (Exception e) {
                logger.error("Erro ao processar/enriquecer produto {}: {}", entry.getKey(), e.getMessage());
                ConsumerRecord<String, String> failedRecord = records.stream()
                   .filter(r -> r.value().contains(entry.getKey()))
                   .findFirst()
                   .orElse(records.get(0));
                throw new CompletionException(
                    new org.springframework.kafka.listener.BatchListenerFailedException("Falha ao preparar comando de produto", e, failedRecord)
                );
            }
        }, executor))
        .toList();

    try {
        List<ProductCommand> commands = enrichmentFutures.stream()
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .toList();
        batchCommands.addAll(commands);
    } catch (CompletionException e) {
        if (e.getCause() instanceof org.springframework.kafka.listener.BatchListenerFailedException) {
            throw (org.springframework.kafka.listener.BatchListenerFailedException) e.getCause();
        }
        throw e;
    }

    // Fase 3: Indexação em Batch
    try {
        if (!batchCommands.isEmpty()) {
            logger.info("Enviando {} produtos para indexação em batch...", batchCommands.size());
            indexProductUseCase.executeBatch(batchCommands);
        }

        logger.info("Lote processado com sucesso");
        acknowledgment.acknowledge();

    } catch (Exception e) {
        logger.error("Erro na indexação do lote de produtos: {}", e.getMessage(), e);
        currentSpan.setStatus(StatusCode.ERROR, "Indexing Error: " + e.getMessage());
        currentSpan.recordException(e);
        if (!records.isEmpty()) {
            throw new org.springframework.kafka.listener.BatchListenerFailedException("Falha na indexação do lote", e, records.get(0));
        }
        throw new RuntimeException("Falha na indexação do lote", e);
    }
  }

  private ProductCommand processProductUpsertSync(DebeziumCDCEvent cdcEvent) throws Exception {
      ProductPayload productData = objectMapper.convertValue(cdcEvent.getAfter(), ProductPayload.class);
      if (productData == null) {
        logger.warn("Evento CDC sem dados 'after', ignorando");
        return null;
      }

      logger.debug("Processando criação/atualização do produto: {}", productData.getId());

      ProductPayload enrichedProduct = enrichmentService.enrich(productData);
      
      if (!isProductEnrichmentComplete(enrichedProduct)) {
        logger.warn("Produto {} não totalmente enriquecido. Brand: {}, Category: {}, Seller: {}",
            enrichedProduct.getId(),
            enrichedProduct.getBrandName() != null,
            enrichedProduct.getCategoryName() != null,
            enrichedProduct.getSellerName() != null);
      }

      return productMapper.mapProductPayloadToDTO(enrichedProduct);
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
