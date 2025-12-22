package com.marketplace.search.indexing.application.usecases;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.marketplace.search.indexing.application.commands.ProductCommand;
import com.marketplace.search.indexing.application.exceptions.IndexingException;
import com.marketplace.search.indexing.application.mappers.ProductMapper;
import com.marketplace.search.indexing.domain.entities.Product;
import com.marketplace.search.indexing.domain.repositories.CacheRepository;
import com.marketplace.search.indexing.domain.repositories.ProductIndexRepository;

/**
 * Caso de uso para indexação de produtos.
 * Executa operações de indexação de forma assíncrona para não bloquear o
 * caller.
 */
@Service
public class IndexProductUseCase {

    private static final Logger logger = LoggerFactory.getLogger(IndexProductUseCase.class);

    private final ProductIndexRepository indexRepository;
    private final ProductMapper productMapper;
    private final CacheRepository cacheRepository;

    public IndexProductUseCase(ProductIndexRepository indexRepository,
            ProductMapper productMapper, CacheRepository cacheRepository) {
        this.indexRepository = indexRepository;
        this.productMapper = productMapper;
        this.cacheRepository = cacheRepository;
    }

    /**
     * Indexa um único produto de forma assíncrona.
     * Este método retorna imediatamente, permitindo que o caller continue seu
     * processamento.
     * A indexação acontece em background usando o threadpool configurado.
     */
    @Async("asyncIndexingExecutor")
    public CompletableFuture<Void> executeAsync(ProductCommand productDTO) {
        logger.info("Indexing product asynchronously: id={}, title='{}'",
                productDTO.id(), productDTO.title());

        try {
            Product product = productMapper.toDomain(productDTO);
            // ProductFeatures features = new ProductFeatures(
            //         product.getId(),
            //         product.getInfo().getPrice(),
            //         product.getSeller().getReputationScore(),
            //         product.getMetrics().getPopularityScore()   
            // // ... outros campos numéricos
            // );

            indexRepository.indexProduct(product);

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            logger.error("Error indexing product: {}", productDTO.id(), e);
            return CompletableFuture.failedFuture(
                    new IndexingException("Failed to index product: " + productDTO.id(), e));
        }
    }

    /**
     * Indexa múltiplos produtos em lote
     */
    public void executeBatch(List<ProductCommand> productDTOs) {
        logger.info("Indexing batch of {} products", productDTOs.size());

        try {
            List<Product> products = productDTOs.stream()
                    .map(productMapper::toDomain)
                    .collect(Collectors.toList());

            indexRepository.indexDocumentsBatch(products);

            logger.info("Batch indexing completed: {} products", products.size());

        } catch (Exception e) {
            logger.error("Error indexing product batch", e);
            throw new IndexingException("Failed to index product batch", e);
        }
    }

}
