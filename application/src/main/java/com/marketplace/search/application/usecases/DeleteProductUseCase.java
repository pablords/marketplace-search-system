package com.marketplace.search.application.usecases;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.marketplace.search.domain.events.ProductDeletedEvent;
import com.marketplace.search.domain.repositories.ProductIndexRepository;
import com.marketplace.search.domain.valueobjects.ProductId;

/**
 * Caso de uso para deleção de produtos do índice.
 * Executa operações de deleção de forma assíncrona para não bloquear o caller.
 */
@Service
public class DeleteProductUseCase {
    
    private static final Logger logger = LoggerFactory.getLogger(DeleteProductUseCase.class);
    
    private final ProductIndexRepository indexRepository;
    private final ApplicationEventPublisher eventPublisher;

    public DeleteProductUseCase(ProductIndexRepository indexRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.indexRepository = indexRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Remove um produto do índice de forma assíncrona.
     * Este método retorna imediatamente, permitindo que o caller continue seu processamento.
     * A deleção acontece em background usando o threadpool configurado.
     */
    @Async("asyncIndexingExecutor")
    public CompletableFuture<Void> execute(String productId) {
        logger.info("Deleting product from index asynchronously: {}", productId);
        
        try {
            ProductId id = new ProductId(productId);
            
            // Verificar se produto existe no índice
            boolean exists = indexRepository.exists(id);
            
            if (!exists) {
                logger.warn("Product not found in index: {}", productId);
                return CompletableFuture.completedFuture(null);
            }
            
            // Deletar do índice
            indexRepository.deleteProduct(id);
            
            // Publicar evento
            eventPublisher.publishEvent(new ProductDeletedEvent(productId));
            
            logger.info("Product deleted from index: {}", productId);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            logger.error("Error deleting product from index: {}", productId, e);
            return CompletableFuture.failedFuture(
                new DeletionException("Failed to delete product: " + productId, e));
        }
    }
    
    /**
     * Exception para erros de deleção
     */
    public static class DeletionException extends RuntimeException {
        public DeletionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
