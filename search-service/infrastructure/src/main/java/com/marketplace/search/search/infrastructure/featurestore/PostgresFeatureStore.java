package com.marketplace.search.search.infrastructure.featurestore;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.marketplace.search.search.domain.repositories.MLFeatureStore;

/**
 * Implementação do Feature Store Offline usando PostgreSQL
 * Armazena features históricas para treinamento futuro de modelos ML
 * Usa composite key (product_id, calculated_at, version) para manter histórico
 */
@Repository("postgresFeatureStore")
public class PostgresFeatureStore implements MLFeatureStore {

    private static final Logger logger = LoggerFactory.getLogger(PostgresFeatureStore.class);
    
    private static final String DEFAULT_VERSION = "1.0";

    private final ProductFeaturesMLRepository repository;

    @Value("${ml.feature-store.offline.version:1.0}")
    private String defaultVersion;

    public PostgresFeatureStore(ProductFeaturesMLRepository repository) {
        this.repository = repository;
    }

    @Override
    public void saveFeatures(String productId, Map<String, Double> features, long ttlSeconds) {
        // No PostgreSQL, não usamos TTL - armazenamos histórico permanentemente
        saveFeatures(productId, features);
    }

    @Override
    public void saveFeatures(String productId, Map<String, Double> features) {
        if (productId == null || productId.isEmpty()) {
            logger.warn("Tentativa de salvar features com productId nulo ou vazio");
            return;
        }

        if (features == null || features.isEmpty()) {
            logger.warn("Tentativa de salvar features vazias para produto: {}", productId);
            return;
        }

        try {
            ProductFeaturesMLEntity entity = new ProductFeaturesMLEntity();
            entity.setProductId(productId);
            entity.setCalculatedAt(LocalDateTime.now());
            entity.setVersion(defaultVersion != null ? defaultVersion : DEFAULT_VERSION);
            entity.setFeaturesFromMap(features);

            repository.save(entity);
            
            logger.debug("Features salvas no Feature Store offline para produto: {} (versão: {}, total: {} features)", 
                productId, entity.getVersion(), features.size());
            
        } catch (Exception e) {
            logger.error("Erro ao salvar features no PostgreSQL para produto: {}", productId, e);
            throw new RuntimeException("Falha ao salvar features no Feature Store offline", e);
        }
    }

    @Override
    public Optional<Map<String, Double>> getFeatures(String productId) {
        if (productId == null || productId.isEmpty()) {
            logger.warn("Tentativa de buscar features com productId nulo ou vazio");
            return Optional.empty();
        }

        try {
            List<ProductFeaturesMLEntity> entities = repository.findLatestByProductId(productId);
            
            if (entities == null || entities.isEmpty()) {
                logger.debug("Features não encontradas no Feature Store offline para produto: {}", productId);
                return Optional.empty();
            }

            // Retornar a versão mais recente
            ProductFeaturesMLEntity latest = entities.get(0);
            Map<String, Double> features = latest.getFeaturesAsMap();

            logger.debug("Features recuperadas do Feature Store offline para produto: {} (versão: {}, total: {} features)", 
                productId, latest.getVersion(), features.size());
            
            return Optional.of(features);
            
        } catch (Exception e) {
            logger.error("Erro ao buscar features no PostgreSQL para produto: {}", productId, e);
            return Optional.empty();
        }
    }

    @Override
    public Map<String, Map<String, Double>> getFeaturesBatch(List<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Map<String, Double>> result = new HashMap<>();
        
        try {
            List<ProductFeaturesMLEntity> entities = repository.findLatestByProductIds(productIds);
            
            for (ProductFeaturesMLEntity entity : entities) {
                Map<String, Double> features = entity.getFeaturesAsMap();
                result.put(entity.getProductId(), features);
            }

            logger.debug("Recuperadas features em lote do Feature Store offline: {}/{} produtos encontrados", 
                result.size(), productIds.size());
            
        } catch (Exception e) {
            logger.error("Erro ao buscar features em lote no PostgreSQL", e);
        }
        
        return result;
    }

    @Override
    public void deleteFeatures(String productId) {
        if (productId == null || productId.isEmpty()) {
            logger.warn("Tentativa de deletar features com productId nulo ou vazio");
            return;
        }

        try {
            List<ProductFeaturesMLEntity> entities = repository.findLatestByProductId(productId);
            if (!entities.isEmpty()) {
                repository.deleteAll(entities);
                logger.debug("Features deletadas do Feature Store offline para produto: {}", productId);
            } else {
                logger.debug("Features não encontradas para deletar (produto: {})", productId);
            }
            
        } catch (Exception e) {
            logger.error("Erro ao deletar features no PostgreSQL para produto: {}", productId, e);
        }
    }

    @Override
    public boolean exists(String productId) {
        if (productId == null || productId.isEmpty()) {
            return false;
        }

        try {
            List<ProductFeaturesMLEntity> entities = repository.findLatestByProductId(productId);
            return !entities.isEmpty();
            
        } catch (Exception e) {
            logger.error("Erro ao verificar existência de features no PostgreSQL para produto: {}", productId, e);
            return false;
        }
    }

    /**
     * Método adicional para buscar features históricas (útil para treinamento)
     * 
     * @param startDate Data inicial
     * @param endDate Data final
     * @return Lista de features no intervalo
     */
    public List<ProductFeaturesMLEntity> getFeaturesByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return repository.findByDateRange(startDate, endDate);
    }
}

