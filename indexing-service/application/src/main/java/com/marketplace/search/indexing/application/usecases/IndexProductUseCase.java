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
import com.marketplace.search.indexing.application.services.ProductFeatureCalculationService;
import com.marketplace.search.indexing.domain.entities.Product;
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
    private final ProductFeatureCalculationService featureCalculationService;

    public IndexProductUseCase(ProductIndexRepository indexRepository,
            ProductMapper productMapper,
            ProductFeatureCalculationService featureCalculationService) {
        this.indexRepository = indexRepository;
        this.productMapper = productMapper;
        this.featureCalculationService = featureCalculationService;
    }

    /**
     * Indexa um único produto de forma assíncrona.
     * Este método retorna imediatamente, permitindo que o caller continue seu
     * processamento.
     * A indexação acontece em background usando o threadpool configurado.
     */
    @Async("asyncIndexingExecutor")
    public CompletableFuture<Void> executeAsync(ProductCommand productCommand) {
        logger.info("Indexing product asynchronously: id={}, title='{}'",
        productCommand.id(), productCommand.title());

        try {
            Product product = productMapper.toDomain(productCommand);

            // Indexar produto no OpenSearch
            indexRepository.indexProduct(product);

            // Calcular e cachear features de ML no Redis
            featureCalculationService.calculateAndCacheFeatures(product);

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            logger.error("Error indexing product: {}", productCommand.id(), e);
            return CompletableFuture.failedFuture(
                    new IndexingException("Failed to index product: " + productCommand.id(), e));
        }
    }

    /**
     * Indexa múltiplos produtos em lote
     */
    public void executeBatch(List<ProductCommand> productCommand) {
        logger.info("Indexing batch of {} products", productCommand.size());

        try {
            List<Product> products = productCommand.stream()
                    .map(productMapper::toDomain)
                    .collect(Collectors.toList());

            // Indexar produtos no OpenSearch
            indexRepository.indexDocumentsBatch(products);

            // Calcular e cachear features de ML para cada produto
            for (Product product : products) {
                featureCalculationService.calculateAndCacheFeatures(product);
            }

            logger.info("Batch indexing completed: {} products", products.size());

        } catch (Exception e) {
            logger.error("Error indexing product batch", e);
            throw new IndexingException("Failed to index product batch", e);
        }
    }

}
