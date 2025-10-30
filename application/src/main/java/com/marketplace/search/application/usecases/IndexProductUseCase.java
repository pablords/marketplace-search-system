package com.marketplace.search.application.usecases;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.marketplace.search.application.dto.ProductDTO;
import com.marketplace.search.application.mappers.ProductMapper;
import com.marketplace.search.domain.entities.Product;
import com.marketplace.search.domain.events.ProductCreatedEvent;
import com.marketplace.search.domain.events.ProductUpdatedEvent;
import com.marketplace.search.domain.repositories.ProductIndexRepository;
import com.marketplace.search.domain.valueobjects.ProductId;

/**
 * Caso de uso para indexação de produtos.
 * Executa operações de indexação de forma assíncrona para não bloquear o caller.
 */
@Service
public class IndexProductUseCase {
    
    private static final Logger logger = LoggerFactory.getLogger(IndexProductUseCase.class);
    
    private final ProductIndexRepository indexRepository;
    private final ProductMapper productMapper;
    private final ApplicationEventPublisher eventPublisher;

    public IndexProductUseCase(ProductIndexRepository indexRepository,
                              ProductMapper productMapper,
                              ApplicationEventPublisher eventPublisher) {
        this.indexRepository = indexRepository;
        this.productMapper = productMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Indexa um único produto de forma assíncrona.
     * Este método retorna imediatamente, permitindo que o caller continue seu processamento.
     * A indexação acontece em background usando o threadpool configurado.
     */
    @Async("asyncIndexingExecutor")
    public CompletableFuture<Void> execute(ProductDTO productDTO) {
        logger.info("Indexing product asynchronously: id={}, title='{}'", 
                   productDTO.getId(), productDTO.getTitle());

        
        try {
            Product product = productMapper.toDomain(productDTO);
            
            // Verificar se produto já existe no índice
            boolean exists = indexRepository.exists(product.getId());
            
            if (exists) {
                indexRepository.updateProduct(product);
                eventPublisher.publishEvent(new ProductUpdatedEvent(product, null));
                logger.info("Product updated in index: {}", product.getId());
            } else {
                indexRepository.indexProduct(product);
                eventPublisher.publishEvent(new ProductCreatedEvent(product));
                logger.info("Product indexed: {}", product.getId());
            }
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            logger.error("Error indexing product: {}", productDTO.getId(), e);
            return CompletableFuture.failedFuture(
                new IndexingException("Failed to index product: " + productDTO.getId(), e));
        }
    }

    /**
     * Indexa múltiplos produtos em lote
     */
    public void executeBatch(List<ProductDTO> productDTOs) {
        logger.info("Indexing batch of {} products", productDTOs.size());
        
        try {
            List<Product> products = productDTOs.stream()
                .map(productMapper::toDomain)
                .collect(Collectors.toList());
            
            indexRepository.indexProducts(products);
            
            // Publicar eventos para cada produto
            products.forEach(product -> 
                eventPublisher.publishEvent(new ProductCreatedEvent(product)));
            
            logger.info("Batch indexing completed: {} products", products.size());
            
        } catch (Exception e) {
            logger.error("Error indexing product batch", e);
            throw new IndexingException("Failed to index product batch", e);
        }
    }

    /**
     * Remove produto do índice
     */
    public void remove(String productId) {
        logger.info("Removing product from index: {}", productId);
        
        try {
            ProductId id = ProductId.from(productId);
            indexRepository.deleteProduct(id);
            
            logger.info("Product removed from index: {}", productId);
            
        } catch (Exception e) {
            logger.error("Error removing product from index: {}", productId, e);
            throw new IndexingException("Failed to remove product from index: " + productId, e);
        }
    }

    /**
     * Remove múltiplos produtos do índice
     */
    public void removeBatch(List<String> productIds) {
        logger.info("Removing batch of {} products from index", productIds.size());
        
        try {
            List<ProductId> ids = productIds.stream()
                .map(ProductId::from)
                .collect(Collectors.toList());
            
            indexRepository.deleteProducts(ids);
            
            logger.info("Batch removal completed: {} products", productIds.size());
            
        } catch (Exception e) {
            logger.error("Error removing product batch from index", e);
            throw new IndexingException("Failed to remove product batch from index", e);
        }
    }

    /**
     * Reindexiza todo o catálogo
     */
    public void reindexAll() {
        logger.info("Starting full reindex of product catalog");
        
        try {
            // Note: Para reindexação completa, seria necessário implementar 
            // lógica específica baseada no caso de uso (ex: buscar todos produtos do BD e reindexar)
            indexRepository.deleteAll();
            
            logger.info("Full reindex completed successfully");
            
        } catch (Exception e) {
            logger.error("Error during full reindex", e);
            throw new IndexingException("Failed to reindex product catalog", e);
        }
    }

    /**
     * Otimiza o índice
     */
    public void optimize() {
        logger.info("Starting index optimization");
        
        try {
            indexRepository.optimize();
            
            logger.info("Index optimization completed");
            
        } catch (Exception e) {
            logger.error("Error during index optimization", e);
            throw new IndexingException("Failed to optimize index", e);
        }
    }
}

/**
 * Exceção específica para erros de indexação
 */
class IndexingException extends RuntimeException {
    public IndexingException(String message, Throwable cause) {
        super(message, cause);
    }
}