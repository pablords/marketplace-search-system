package com.marketplace.search.application.handlers;

import com.marketplace.search.application.dto.ProductDTO;
import com.marketplace.search.application.usecases.IndexProductUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Handler para processar eventos de produto recebidos via Kafka
 */
@Component
public class ProductEventHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductEventHandler.class);
    
    private final IndexProductUseCase indexProductUseCase;
    private final ObjectMapper objectMapper;

    public ProductEventHandler(IndexProductUseCase indexProductUseCase, ObjectMapper objectMapper) {
        this.indexProductUseCase = indexProductUseCase;
        this.objectMapper = objectMapper;
    }

    /**
     * Processa eventos de criação de produto
     */
    @KafkaListener(topics = "product.created", groupId = "search-service")
    public void handleProductCreated(String message) {
        logger.info("Received product created event: {}", message);
        
        try {
            ProductCreatedEventPayload payload = objectMapper.readValue(message, ProductCreatedEventPayload.class);
            
            ProductDTO productDTO = mapToProductDTO(payload);
            indexProductUseCase.execute(productDTO);
            
            logger.info("Successfully processed product created event for product: {}", payload.getProductId());
            
        } catch (Exception e) {
            logger.error("Error processing product created event: {}", message, e);
            // Em um ambiente real, seria enviado para uma DLQ (Dead Letter Queue)
        }
    }

    /**
     * Processa eventos de atualização de produto
     */
    @KafkaListener(topics = "product.updated", groupId = "search-service")
    public void handleProductUpdated(String message) {
        logger.info("Received product updated event: {}", message);
        
        try {
            ProductUpdatedEventPayload payload = objectMapper.readValue(message, ProductUpdatedEventPayload.class);
            
            ProductDTO productDTO = mapToProductDTO(payload);
            indexProductUseCase.execute(productDTO);
            
            logger.info("Successfully processed product updated event for product: {}", payload.getProductId());
            
        } catch (Exception e) {
            logger.error("Error processing product updated event: {}", message, e);
        }
    }

    /**
     * Processa eventos de remoção de produto
     */
    @KafkaListener(topics = "product.deleted", groupId = "search-service")
    public void handleProductDeleted(String message) {
        logger.info("Received product deleted event: {}", message);
        
        try {
            ProductDeletedEventPayload payload = objectMapper.readValue(message, ProductDeletedEventPayload.class);
            
            indexProductUseCase.remove(payload.getProductId());
            
            logger.info("Successfully processed product deleted event for product: {}", payload.getProductId());
            
        } catch (Exception e) {
            logger.error("Error processing product deleted event: {}", message, e);
        }
    }

    /**
     * Processa eventos de atualização de preço
     */
    @KafkaListener(topics = "product.price.updated", groupId = "search-service")
    public void handleProductPriceUpdated(String message) {
        logger.info("Received product price updated event: {}", message);
        
        try {
            ProductPriceUpdatedEventPayload payload = objectMapper.readValue(message, ProductPriceUpdatedEventPayload.class);
            
            // Para atualização de preço, precisaríamos buscar o produto completo
            // e atualizar apenas o preço no índice
            logger.info("Processing price update for product: {} - new price: {}", 
                       payload.getProductId(), payload.getNewPrice());
            
            // Implementação específica para atualização de preço seria feita aqui
            
        } catch (Exception e) {
            logger.error("Error processing product price updated event: {}", message, e);
        }
    }

    /**
     * Processa eventos de atualização de estoque
     */
    @KafkaListener(topics = "product.stock.updated", groupId = "search-service")
    public void handleProductStockUpdated(String message) {
        logger.info("Received product stock updated event: {}", message);
        
        try {
            ProductStockUpdatedEventPayload payload = objectMapper.readValue(message, ProductStockUpdatedEventPayload.class);
            
            logger.info("Processing stock update for product: {} - new quantity: {}", 
                       payload.getProductId(), payload.getNewQuantity());
            
            // Implementação específica para atualização de estoque seria feita aqui
            
        } catch (Exception e) {
            logger.error("Error processing product stock updated event: {}", message, e);
        }
    }

    private ProductDTO mapToProductDTO(ProductCreatedEventPayload payload) {
        // Mapeamento simplificado - em um cenário real seria mais complexo
        ProductDTO dto = new ProductDTO();
        dto.setId(payload.getProductId());
        dto.setTitle(payload.getTitle());
        dto.setDescription(payload.getDescription());
        dto.setPrice(payload.getPrice());
        dto.setCurrency(payload.getCurrency());
        // ... outros campos seriam mapeados
        return dto;
    }

    private ProductDTO mapToProductDTO(ProductUpdatedEventPayload payload) {
        // Similar ao método acima, mas para eventos de atualização
        ProductDTO dto = new ProductDTO();
        dto.setId(payload.getProductId());
        dto.setTitle(payload.getTitle());
        dto.setDescription(payload.getDescription());
        dto.setPrice(payload.getPrice());
        dto.setCurrency(payload.getCurrency());
        return dto;
    }
}