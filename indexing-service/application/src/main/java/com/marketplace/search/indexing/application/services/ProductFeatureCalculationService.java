package com.marketplace.search.indexing.application.services;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.marketplace.search.indexing.domain.entities.Product;
import com.marketplace.search.indexing.domain.repositories.MLFeatureStore;
import com.marketplace.search.indexing.domain.services.FeatureExtractor;
import com.marketplace.search.indexing.domain.valueobjects.ProductId;

/**
 * Serviço responsável por calcular e cachear features de ML durante a indexação de produtos.
 * 
 * Este serviço:
 * 1. Calcula features estáticas do produto (que não dependem de query de busca)
 * 2. Armazena as features no Redis Feature Store para acesso rápido durante buscas
 * 
 * Features que dependem de query (bm25_score, knn_score, exact_match, etc.) serão
 * calculadas e atualizadas durante a busca pelo search-service.
 */
@Service
public class ProductFeatureCalculationService {

    private static final Logger logger = LoggerFactory.getLogger(ProductFeatureCalculationService.class);

    private final FeatureExtractor featureExtractor;
    private final MLFeatureStore featureStore;

    public ProductFeatureCalculationService(
            FeatureExtractor featureExtractor,
            MLFeatureStore featureStore) {
        this.featureExtractor = featureExtractor;
        this.featureStore = featureStore;
    }

    /**
     * Calcula e cacheia features de ML para um produto durante a indexação.
     * 
     * @param product Produto a ser processado
     */
    public void calculateAndCacheFeatures(Product product) {
        if (product == null) {
            logger.warn("Tentativa de calcular features para produto nulo");
            return;
        }

        ProductId productId = product.getId();
        if (productId == null) {
            logger.warn("Tentativa de calcular features para produto sem ID");
            return;
        }

        try {
            // Extrair features estáticas (que não dependem de query)
            Map<String, Double> features = featureExtractor.extractStaticFeatures(product);
            
            // Cachear features no Redis
            featureStore.saveFeatures(productId.getValue(), features);
            
            logger.debug("Features calculadas e cacheadas para produto: {} (total: {} features)", 
                productId.getValue(), features.size());
            
        } catch (Exception e) {
            logger.error("Erro ao calcular e cachear features para produto: {}", 
                productId != null ? productId.getValue() : "null", e);
            // Não propagar exceção para não interromper o fluxo de indexação
            // Features podem ser calculadas on-the-fly durante a busca se necessário
        }
    }

    /**
     * Remove features de um produto do cache (útil quando produto é deletado)
     * 
     * @param productId ID do produto
     */
    public void deleteCachedFeatures(String productId) {
        if (productId == null || productId.isEmpty()) {
            logger.warn("Tentativa de deletar features com productId nulo ou vazio");
            return;
        }

        try {
            featureStore.deleteFeatures(productId);
            logger.debug("Features removidas do cache para produto: {}", productId);
        } catch (Exception e) {
            logger.error("Erro ao deletar features do cache para produto: {}", productId, e);
        }
    }
}

