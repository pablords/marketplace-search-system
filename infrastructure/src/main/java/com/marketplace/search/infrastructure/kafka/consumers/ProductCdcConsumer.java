package com.marketplace.search.infrastructure.kafka.consumers;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.search.application.dto.ProductDTO;
import com.marketplace.search.application.usecases.DeleteProductUseCase;
import com.marketplace.search.application.usecases.IndexProductUseCase;
import com.marketplace.search.infrastructure.kafka.dto.DebeziumEventDTO;
import com.marketplace.search.infrastructure.kafka.dto.ProductPayloadDTO;
import com.marketplace.search.infrastructure.kafka.mappers.DebeziumProductMapper;

/**
 * Consumer responsável por processar eventos de CDC do Debezium sobre produtos.
 * Quando um produto é criado, atualizado ou deletado no PostgreSQL, o Debezium
 * captura essa mudança e publica no Kafka. Este consumer processa esses eventos
 * e sincroniza com o Elasticsearch.
 */
@Service
public class ProductCdcConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductCdcConsumer.class);
    
    private final ObjectMapper objectMapper;
    private final DebeziumProductMapper debeziumMapper;
    private final IndexProductUseCase indexProductUseCase;
    private final DeleteProductUseCase deleteProductUseCase;
    
    public ProductCdcConsumer(
            ObjectMapper objectMapper,
            DebeziumProductMapper debeziumMapper,
            IndexProductUseCase indexProductUseCase,
            DeleteProductUseCase deleteProductUseCase) {
        this.objectMapper = objectMapper;
        this.debeziumMapper = debeziumMapper;
        this.indexProductUseCase = indexProductUseCase;
        this.deleteProductUseCase = deleteProductUseCase;
    }
    
    /**
     * Consome eventos do tópico configurado no Debezium.
     * O Debezium envia eventos com a estrutura:
     * {
     *   "schema": {...},
     *   "payload": {
     *     "before": {...},
     *     "after": {...},
     *     "op": "c|u|d|r",
     *     "ts_ms": 123456
     *   }
     * }
     */
    @KafkaListener(
        topics = "${kafka.topics.product-events}",
        groupId = "${kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeProductEvent(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            logger.info("Received CDC event from topic: {}, partition: {}, offset: {}", 
                       record.topic(), record.partition(), record.offset());
            
            // Parse o envelope do Debezium
            JsonNode rootNode = objectMapper.readTree(record.value());
            JsonNode payloadNode = rootNode.get("payload");
            
            if (payloadNode == null) {
                logger.warn("Event without payload, skipping...");
                acknowledgment.acknowledge();
                return;
            }
            
            // Parse o payload do evento
            DebeziumEventDTO event = objectMapper.treeToValue(payloadNode, DebeziumEventDTO.class);
            
            // Processa baseado no tipo de operação
            // IMPORTANTE: processEvent() dispara operações assíncronas (@Async)
            // A indexação acontece em background, não bloqueando o consumer
            processEvent(event);
            
            // Confirma o processamento da mensagem imediatamente
            // A indexação assíncrona continuará em background
            acknowledgment.acknowledge();
            
            logger.info("CDC event received and dispatched for async processing: operation={}, productId={}", 
                       event.getOperation(), 
                       event.getAfter() != null ? event.getAfter().getId() : 
                       event.getBefore() != null ? event.getBefore().getId() : "unknown");
            
        } catch (JsonProcessingException e) {
            logger.error("Error parsing CDC event: {}", e.getMessage(), e);
            // Não faz acknowledge em caso de erro de parse
            // A mensagem será reprocessada
        } catch (Exception e) {
            logger.error("Error processing CDC event: {}", e.getMessage(), e);
            // Acknowledge mesmo com erro para não travar a fila
            // TODO: Implementar Dead Letter Queue (DLQ) para mensagens com erro
            acknowledgment.acknowledge();
        }
    }
    
    /**
     * Processa o evento baseado no tipo de operação do CDC
     */
    private void processEvent(DebeziumEventDTO event) {
        String operation = event.getOperation();
        
        switch (operation) {
            case "c": // CREATE (insert)
            case "r": // READ (snapshot inicial do Debezium)
                handleCreateOrUpdate(event.getAfter(), "CREATE");
                break;
                
            case "u": // UPDATE
                handleCreateOrUpdate(event.getAfter(), "UPDATE");
                break;
                
            case "d": // DELETE
                handleDelete(event.getBefore());
                break;
                
            default:
                logger.warn("Unknown CDC operation: {}", operation);
        }
    }
    
    /**
     * Processa criação ou atualização de produto.
     * Dispara indexação assíncrona que acontecerá em background.
     */
    private void handleCreateOrUpdate(ProductPayloadDTO payload, String operationType) {
        if (payload == null) {
            logger.warn("Payload is null for {} operation", operationType);
            return;
        }
        
        try {
            logger.info("Dispatching async {} operation for product: {}", operationType, payload.getId());
            
            // Converte payload do Debezium para ProductDTO
            ProductDTO productDTO = debeziumMapper.toProductDTO(payload);
            
            // Dispara indexação assíncrona no Elasticsearch
            // O método retorna CompletableFuture, mas não esperamos o resultado
            // A indexação continuará em background
            indexProductUseCase.execute(productDTO);
            
            logger.debug("Product {} dispatched for async indexing: {}", 
                       operationType.toLowerCase(), payload.getId());
            
        } catch (Exception e) {
            logger.error("Error dispatching product for indexing {}: {}", payload.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to dispatch product for indexing: " + payload.getId(), e);
        }
    }
    
    /**
     * Processa deleção de produto.
     * Dispara deleção assíncrona que acontecerá em background.
     */
    private void handleDelete(ProductPayloadDTO payload) {
        if (payload == null) {
            logger.warn("Payload is null for DELETE operation");
            return;
        }
        
        try {
            logger.info("Dispatching async DELETE operation for product: {}", payload.getId());
            
            // Dispara deleção assíncrona do Elasticsearch
            // O método retorna CompletableFuture, mas não esperamos o resultado
            // A deleção continuará em background
            deleteProductUseCase.execute(payload.getId());
            
            logger.debug("Product dispatched for async deletion: {}", payload.getId());
            
        } catch (Exception e) {
            logger.error("Error dispatching product for deletion {}: {}", payload.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to dispatch product for deletion: " + payload.getId(), e);
        }
    }
}
