package com.marketplace.search.search.domain.repositories;

import java.util.Map;
import java.util.Optional;

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
     * @param ttlSeconds TTL em segundos (padrão: 1 hora = 3600 segundos)
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
     * Recupera as features de ML de um produto do cache
     * 
     * @param productId ID do produto
     * @return Optional contendo o mapa de features, ou empty se não encontrado
     */
    Optional<Map<String, Double>> getFeatures(String productId);

    /**
     * Recupera múltiplas features de ML de vários produtos
     * 
     * @param productIds Lista de IDs dos produtos
     * @return Mapa de productId -> features (apenas produtos encontrados no cache)
     */
    Map<String, Map<String, Double>> getFeaturesBatch(java.util.List<String> productIds);

    /**
     * Remove as features de um produto do cache
     * 
     * @param productId ID do produto
     */
    void deleteFeatures(String productId);

    /**
     * Verifica se as features de um produto estão no cache
     * 
     * @param productId ID do produto
     * @return true se as features existem no cache
     */
    boolean exists(String productId);
}


