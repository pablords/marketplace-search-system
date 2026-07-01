package com.marketplace.search.indexing.application.usecases;


import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;


import java.util.concurrent.Executor;

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
import org.springframework.beans.factory.annotation.Qualifier;
@Service
public class IndexProductUseCase {

    private static final Logger logger = LoggerFactory.getLogger(IndexProductUseCase.class);

    private final ProductIndexRepository indexRepository;
    private final ProductMapper productMapper;
    private final ProductFeatureCalculationService featureCalculationService;
    private final MeterRegistry meterRegistry;
    private final Executor executor;

    public IndexProductUseCase(ProductIndexRepository indexRepository,
            ProductMapper productMapper,
            ProductFeatureCalculationService featureCalculationService,
            MeterRegistry meterRegistry,
            @Qualifier("applicationTaskExecutor") Executor executor) {
        this.indexRepository = indexRepository;
        this.productMapper = productMapper;
        this.featureCalculationService = featureCalculationService;
        this.meterRegistry = meterRegistry;
        this.executor = executor;
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
            meterRegistry.counter("indexing.events.consumed.total", "status", "received").increment();
            Timer.Sample totalSample = Timer.start(meterRegistry);

            Product product = productMapper.toDomain(productCommand);

            // Indexar produto no OpenSearch
            Timer.Sample osSample = Timer.start(meterRegistry);
            indexRepository.indexProduct(product);
            osSample.stop(Timer.builder("indexing.opensearch.duration").register(meterRegistry));

            // Calcular e cachear features de ML no Redis
            Timer.Sample featureSample = Timer.start(meterRegistry);
            featureCalculationService.calculateAndCacheFeatures(product);
            featureSample.stop(Timer.builder("indexing.features.duration").register(meterRegistry));

            totalSample.stop(Timer.builder("indexing.process.duration")
                .tag("status", "success")
                .register(meterRegistry));

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            meterRegistry.counter("indexing.events.consumed.total", "status", "error").increment();
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
            meterRegistry.counter("indexing.events.consumed.total", "status", "received_batch").increment(productCommand.size());
            Timer.Sample totalSample = Timer.start(meterRegistry);

            List<Product> products = productCommand.stream()
                    .map(productMapper::toDomain)
                    .collect(Collectors.toList());

            // Indexar produtos no OpenSearch
            Timer.Sample osSample = Timer.start(meterRegistry);
            indexRepository.indexDocumentsBatch(products);
            osSample.stop(Timer.builder("indexing.opensearch.batch.duration").register(meterRegistry));

            // Calcular e cachear features de ML para cada produto
            Timer.Sample featureSample = Timer.start(meterRegistry);
            List<CompletableFuture<Void>> featureFutures = products.stream()
                .map(product -> CompletableFuture.runAsync(() -> 
                    featureCalculationService.calculateAndCacheFeatures(product)
                , executor))
                .collect(Collectors.toList());
            CompletableFuture.allOf(featureFutures.toArray(new CompletableFuture[0])).join();
            featureSample.stop(Timer.builder("indexing.features.batch.duration").register(meterRegistry));

            totalSample.stop(Timer.builder("indexing.process.batch.duration")
                .tag("status", "success")
                .register(meterRegistry));

            logger.info("Batch indexing completed: {} products", products.size());

        } catch (Exception e) {
            meterRegistry.counter("indexing.events.consumed.total", "status", "error_batch").increment(productCommand.size());
            logger.error("Error indexing product batch", e);
            throw new IndexingException("Failed to index product batch", e);
        }
    }

}
