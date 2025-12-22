package com.marketplace.search.indexing.infrastructure.featurestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import com.marketplace.search.indexing.domain.repositories.MLFeatureStore;

/**
 * Implementação do Feature Store Online usando Redis para o indexing-service.
 * Armazena features de ML usando Hash Redis com namespace feature:ml:{product_id}
 * TTL padrão: 1 hora (3600 segundos)
 */
@Repository
public class RedisFeatureStore implements MLFeatureStore {

    private static final Logger logger = LoggerFactory.getLogger(RedisFeatureStore.class);
    
    private static final String KEY_PREFIX = "feature:ml:";
    private static final long DEFAULT_TTL_SECONDS = 3600L; // 1 hora

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${ml.feature-store.ttl-seconds:3600}")
    private long defaultTtlSeconds;

    public RedisFeatureStore(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Gera a chave Redis para um produto
     */
    private String buildKey(String productId) {
        return KEY_PREFIX + productId;
    }

    @Override
    public void saveFeatures(String productId, Map<String, Double> features, long ttlSeconds) {
        if (productId == null || productId.isEmpty()) {
            logger.warn("Tentativa de salvar features com productId nulo ou vazio");
            return;
        }

        if (features == null || features.isEmpty()) {
            logger.warn("Tentativa de salvar features vazias para produto: {}", productId);
            return;
        }

        try {
            String key = buildKey(productId);
            
            // Usar Hash Redis para armazenar cada feature como um campo
            // Cada feature é serializada como string para facilitar leitura
            Map<String, String> hashFields = new HashMap<>();
            for (Map.Entry<String, Double> entry : features.entrySet()) {
                hashFields.put(entry.getKey(), String.valueOf(entry.getValue()));
            }

            // Salvar todas as features como hash
            redisTemplate.opsForHash().putAll(key, hashFields);
            
            // Definir TTL
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
            
            logger.debug("Features salvas no cache para produto: {} com TTL: {}s (total: {} features)", 
                productId, ttlSeconds, features.size());
            
        } catch (Exception e) {
            logger.error("Erro ao salvar features no Redis para produto: {}", productId, e);
            throw new RuntimeException("Falha ao salvar features no Feature Store", e);
        }
    }

    @Override
    public void saveFeatures(String productId, Map<String, Double> features) {
        saveFeatures(productId, features, defaultTtlSeconds > 0 ? defaultTtlSeconds : DEFAULT_TTL_SECONDS);
    }

    @Override
    public void deleteFeatures(String productId) {
        if (productId == null || productId.isEmpty()) {
            logger.warn("Tentativa de deletar features com productId nulo ou vazio");
            return;
        }

        try {
            String key = buildKey(productId);
            Boolean deleted = redisTemplate.delete(key);
            
            if (Boolean.TRUE.equals(deleted)) {
                logger.debug("Features deletadas do cache para produto: {}", productId);
            } else {
                logger.debug("Features não encontradas para deletar (produto: {})", productId);
            }
            
        } catch (Exception e) {
            logger.error("Erro ao deletar features no Redis para produto: {}", productId, e);
        }
    }
}

