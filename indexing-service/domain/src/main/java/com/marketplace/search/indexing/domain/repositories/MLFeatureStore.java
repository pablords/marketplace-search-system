package com.marketplace.search.indexing.domain.repositories;

import java.util.Map;

/**
 * Interface do Feature Store para armazenar e recuperar features de ML
 * Usado para cachear features pré-calculadas e acelerar o processo de ranking
 */
public interface MLFeatureStore {

    /**
     * Salva as features de ML de um produto no cache
     * 
     * @param productId ID do produto
     * @param features Mapa com as features (nome -> valor)
     * @param ttlSeconds TTL em segundos
     */
    void saveFeatures(String productId, Map<String, Double> features, long ttlSeconds);

    /**
     * Salva as features de ML de um produto no cache com TTL padrão (1 hora)
     * 
     * @param productId ID do produto
     * @param features Mapa com as features (nome -> valor)
     */
    void saveFeatures(String productId, Map<String, Double> features);

    /**
     * Remove as features de um produto do cache
     * 
     * @param productId ID do produto
     */
    void deleteFeatures(String productId);
}

