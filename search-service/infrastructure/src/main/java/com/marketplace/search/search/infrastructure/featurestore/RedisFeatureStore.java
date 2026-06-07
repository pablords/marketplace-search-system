package com.marketplace.search.search.infrastructure.featurestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.marketplace.search.search.domain.repositories.MLFeatureStore;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Implementação do Feature Store Online usando Redis
 * Armazena features de ML usando Hash Redis com namespace feature:ml:{product_id}
 * TTL padrão: 1 hora (3600 segundos)
 * 
 * Marcado como @Primary pois é o Feature Store usado para cache rápido durante o ranking.
 * O PostgresFeatureStore é usado apenas para armazenamento histórico offline.
 */
@Repository
@Primary
public class RedisFeatureStore implements MLFeatureStore {

    private static final Logger logger = LoggerFactory.getLogger(RedisFeatureStore.class);
    
    private static final String KEY_PREFIX = "feature:ml:";
    private static final long DEFAULT_TTL_SECONDS = 3600L; // 1 hora

    private final RedisTemplate<String, String> redisTemplate;
    private final Cache<String, Map<String, Double>> l1Cache;
    private final MeterRegistry meterRegistry;

    @Value("${ml.feature-store.ttl-seconds:3600}")
    private long defaultTtlSeconds;

    public RedisFeatureStore(
            RedisTemplate<String, String> redisTemplate,
            MeterRegistry meterRegistry,
            @Value("${ml.feature-store.l1.ttl-seconds:300}") long l1TtlSeconds,
            @Value("${ml.feature-store.l1.max-size:10000}") long l1MaxSize) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.l1Cache = Caffeine.newBuilder()
            .expireAfterWrite(l1TtlSeconds, TimeUnit.SECONDS)
            .maximumSize(l1MaxSize)
            .build();
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

        // Salvar no L1 Cache
        l1Cache.put(productId, features);

        try {
            String key = buildKey(productId);
            
            // Usar Hash Redis para armazenar cada feature como um campo
            // Cada feature é serializada como JSON string para facilitar leitura
            Map<String, String> hashFields = new HashMap<>();
            for (Map.Entry<String, Double> entry : features.entrySet()) {
                hashFields.put(entry.getKey(), String.valueOf(entry.getValue()));
            }

            // Salvar todas as features como hash
            redisTemplate.opsForHash().putAll(key, hashFields);
            
            // Definir TTL
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
            
            logger.debug("Features salvas no L1 + L2 (Redis) para produto: {} com TTL: {}s (total: {} features)", 
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
    public Optional<Map<String, Double>> getFeatures(String productId) {
        if (productId == null || productId.isEmpty()) {
            logger.warn("Tentativa de buscar features com productId nulo ou vazio");
            return Optional.empty();
        }

        // 1. Check L1 Cache
        Map<String, Double> cachedL1 = l1Cache.getIfPresent(productId);
        if (cachedL1 != null) {
            meterRegistry.counter("search.ml.features.l1.cache", "status", "hit").increment();
            logger.debug("L1 Cache HIT para produto: {}", productId);
            return Optional.of(cachedL1);
        }
        meterRegistry.counter("search.ml.features.l1.cache", "status", "miss").increment();

        // 2. Check L2 Cache (Redis)
        try {
            String key = buildKey(productId);
            
            // Verificar se a chave existe
            Boolean exists = redisTemplate.hasKey(key);
            if (Boolean.FALSE.equals(exists)) {
                meterRegistry.counter("search.ml.features.l2.cache", "status", "miss").increment();
                logger.debug("Features não encontradas no cache L2 para produto: {}", productId);
                return Optional.empty();
            }

            // Buscar todos os campos do hash
            Map<Object, Object> hashFields = redisTemplate.opsForHash().entries(key);
            
            if (hashFields == null || hashFields.isEmpty()) {
                meterRegistry.counter("search.ml.features.l2.cache", "status", "miss").increment();
                logger.debug("Hash vazio para produto: {}", productId);
                return Optional.empty();
            }

            // Converter para Map<String, Double>
            Map<String, Double> features = new HashMap<>();
            for (Map.Entry<Object, Object> entry : hashFields.entrySet()) {
                String featureName = entry.getKey().toString();
                try {
                    Double featureValue = Double.parseDouble(entry.getValue().toString());
                    features.put(featureName, featureValue);
                } catch (NumberFormatException e) {
                    logger.warn("Valor inválido para feature {} do produto {}: {}", 
                        featureName, productId, entry.getValue());
                }
            }

            if (!features.isEmpty()) {
                // Populate L1 cache
                l1Cache.put(productId, features);
                meterRegistry.counter("search.ml.features.l2.cache", "status", "hit").increment();
                logger.debug("L2 Cache HIT (salvo em L1) para produto: {} (total: {} features)", 
                    productId, features.size());
                return Optional.of(features);
            }
            
            meterRegistry.counter("search.ml.features.l2.cache", "status", "miss").increment();
            return Optional.empty();
            
        } catch (Exception e) {
            meterRegistry.counter("search.ml.features.l2.cache", "status", "error").increment();
            logger.error("Erro ao buscar features no Redis para produto: {}", productId, e);
            return Optional.empty();
        }
    }

    @Override
    public Map<String, Map<String, Double>> getFeaturesBatch(List<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Map<String, Double>> result = new HashMap<>();
        List<String> missingFromL1 = new java.util.ArrayList<>();

        // 1. Check L1 Cache first
        for (String productId : productIds) {
            Map<String, Double> l1Features = l1Cache.getIfPresent(productId);
            if (l1Features != null) {
                result.put(productId, l1Features);
            } else {
                missingFromL1.add(productId);
            }
        }

        int l1Hits = productIds.size() - missingFromL1.size();
        int l1Misses = missingFromL1.size();
        
        if (l1Hits > 0) {
            meterRegistry.counter("search.ml.features.l1.cache", "status", "hit").increment(l1Hits);
        }
        if (l1Misses > 0) {
            meterRegistry.counter("search.ml.features.l1.cache", "status", "miss").increment(l1Misses);
        }

        if (missingFromL1.isEmpty()) {
            logger.debug("L1 Cache HIT total para todos os {} produtos", productIds.size());
            return result;
        }

        logger.debug("L1 Cache: {} hits, {} misses. Buscando misses no L2 (Redis)", 
            result.size(), missingFromL1.size());

        // 2. Fetch misses in batch from L2 (Redis) using pipeline
        int l2Hits = 0;
        try {
            List<Object> pipelinedResults = redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                    for (String productId : missingFromL1) {
                        String key = buildKey(productId);
                        operations.opsForHash().entries((K) key);
                    }
                    return null;
                }
            });

            if (pipelinedResults != null) {
                for (int i = 0; i < missingFromL1.size() && i < pipelinedResults.size(); i++) {
                    String productId = missingFromL1.get(i);
                    Object obj = pipelinedResults.get(i);
                    if (obj instanceof Map) {
                        Map<?, ?> hashFields = (Map<?, ?>) obj;
                        if (hashFields != null && !hashFields.isEmpty()) {
                            Map<String, Double> features = new HashMap<>();
                            for (Map.Entry<?, ?> entry : hashFields.entrySet()) {
                                String featureName = entry.getKey().toString();
                                try {
                                    Double featureValue = Double.parseDouble(entry.getValue().toString());
                                    features.put(featureName, featureValue);
                                } catch (NumberFormatException e) {
                                    logger.warn("Valor inválido para feature {} do produto {}: {}", 
                                        featureName, productId, entry.getValue());
                                }
                            }
                            if (!features.isEmpty()) {
                                // Write to L1
                                l1Cache.put(productId, features);
                                // Put in final results
                                result.put(productId, features);
                                l2Hits++;
                            }
                        }
                    }
                }
            }
            
            int l2Misses = missingFromL1.size() - l2Hits;
            if (l2Hits > 0) {
                meterRegistry.counter("search.ml.features.l2.cache", "status", "hit").increment(l2Hits);
            }
            if (l2Misses > 0) {
                meterRegistry.counter("search.ml.features.l2.cache", "status", "miss").increment(l2Misses);
            }

            logger.debug("Recuperadas features em lote (pipeline): {}/{} produtos encontrados no L2", 
                l2Hits, missingFromL1.size());
        } catch (Exception e) {
            meterRegistry.counter("search.ml.features.l2.cache", "status", "error").increment();
            logger.error("Erro ao recuperar features em lote do Redis", e);
            // Fallback para get único em caso de erro no pipeline
            for (String productId : missingFromL1) {
                getFeatures(productId).ifPresent(f -> result.put(productId, f));
            }
        }

        return result;
    }

    @Override
    public void saveFeaturesBatch(Map<String, Map<String, Double>> featuresBatch) {
        if (featuresBatch == null || featuresBatch.isEmpty()) {
            return;
        }

        // Salvar no L1 Cache
        l1Cache.putAll(featuresBatch);

        try {
            redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                    long ttl = defaultTtlSeconds > 0 ? defaultTtlSeconds : DEFAULT_TTL_SECONDS;
                    for (Map.Entry<String, Map<String, Double>> batchEntry : featuresBatch.entrySet()) {
                        String productId = batchEntry.getKey();
                        Map<String, Double> features = batchEntry.getValue();
                        String key = buildKey(productId);
                        
                        Map<String, String> hashFields = new HashMap<>();
                        for (Map.Entry<String, Double> entry : features.entrySet()) {
                            hashFields.put(entry.getKey(), String.valueOf(entry.getValue()));
                        }
                        
                        operations.opsForHash().putAll((K) key, hashFields);
                        operations.expire((K) key, ttl, TimeUnit.SECONDS);
                    }
                    return null;
                }
            });
            logger.debug("Salvas {} features estáticas no cache L1 + L2 em lote (pipeline)", featuresBatch.size());
        } catch (Exception e) {
            logger.error("Erro ao salvar features em lote no Redis", e);
            // Fallback para salvamento individual em caso de erro
            for (Map.Entry<String, Map<String, Double>> entry : featuresBatch.entrySet()) {
                try {
                    saveFeatures(entry.getKey(), entry.getValue());
                } catch (Exception ex) {
                    logger.warn("Erro no fallback de salvamento para produto: {}", entry.getKey(), ex);
                }
            }
        }
    }

    @Override
    public void deleteFeatures(String productId) {
        if (productId == null || productId.isEmpty()) {
            logger.warn("Tentativa de deletar features com productId nulo ou vazio");
            return;
        }

        // Invalidate from L1
        l1Cache.invalidate(productId);

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

    @Override
    public boolean exists(String productId) {
        if (productId == null || productId.isEmpty()) {
            return false;
        }

        // Check L1 first
        if (l1Cache.getIfPresent(productId) != null) {
            return true;
        }

        try {
            String key = buildKey(productId);
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
            
        } catch (Exception e) {
            logger.error("Erro ao verificar existência de features no Redis para produto: {}", productId, e);
            return false;
        }
    }
}

