package com.marketplace.search.indexing.infrastructure.redis.repositories;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.search.indexing.domain.repositories.CacheRepository;

/**
 * Implementação do repositório de cache usando Redis
 */
@Repository
public class RedisCacheRepository implements CacheRepository {

    private static final Logger logger = LoggerFactory.getLogger(RedisCacheRepository.class);
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheRepository(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> void put(String key, T value, Duration ttl) {
        try {
            String serializedValue = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, serializedValue, ttl.toSeconds(), TimeUnit.SECONDS);
            
            logger.debug("Cached value for key: {} with TTL: {}", key, ttl);
            
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize value for key: {}", key, e);
            throw new RuntimeException("Failed to cache value", e);
        }
    }

    @Override
    public <T> void put(String key, T value) {
        try {
            String serializedValue = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, serializedValue);
            
            logger.debug("Cached value for key: {} without TTL", key);
            
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize value for key: {}", key, e);
            throw new RuntimeException("Failed to cache value", e);
        }
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String serializedValue = redisTemplate.opsForValue().get(key);
            
            if (serializedValue == null) {
                logger.debug("Cache miss for key: {}", key);
                return Optional.empty();
            }
            
            T value = objectMapper.readValue(serializedValue, type);
            logger.debug("Cache hit for key: {}", key);
            return Optional.of(value);
            
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize value for key: {}", key, e);
            return Optional.empty();
        }
    }

    @Override
    public void evict(String key) {
        Boolean deleted = redisTemplate.delete(key);
        
        if (Boolean.TRUE.equals(deleted)) {
            logger.debug("Evicted cache entry for key: {}", key);
        } else {
            logger.debug("No cache entry found to evict for key: {}", key);
        }
    }

    @Override
    public void evictPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        
        if (keys != null && !keys.isEmpty()) {
            Long deletedCount = redisTemplate.delete(keys);
            logger.debug("Evicted {} cache entries matching pattern: {}", deletedCount, pattern);
        } else {
            logger.debug("No cache entries found matching pattern: {}", pattern);
        }
    }

    @Override
    public boolean exists(String key) {
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void expire(String key, Duration ttl) {
        Boolean result = redisTemplate.expire(key, ttl);
        
        if (Boolean.TRUE.equals(result)) {
            logger.debug("Set TTL {} for key: {}", ttl, key);
        } else {
            logger.debug("Failed to set TTL for key: {} (key may not exist)", key);
        }
    }

    @Override
    public Duration getTtl(String key) {
        Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        
        if (ttlSeconds == null || ttlSeconds < 0) {
            return Duration.ZERO;
        }
        
        return Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public void increment(String key) {
        redisTemplate.opsForValue().increment(key);
        logger.debug("Incremented counter for key: {}", key);
    }

    @Override
    public void increment(String key, long delta) {
        redisTemplate.opsForValue().increment(key, delta);
        logger.debug("Incremented counter for key: {} by {}", key, delta);
    }

    @Override
    public Long getCounter(String key) {
        String value = redisTemplate.opsForValue().get(key);
        
        if (value == null) {
            return 0L;
        }
        
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid counter value for key {}: {}", key, value);
            return 0L;
        }
    }

    @Override
    public void addToSet(String key, String value) {
        redisTemplate.opsForSet().add(key, value);
        logger.debug("Added value to set for key: {}", key);
    }

    @Override
    public void removeFromSet(String key, String value) {
        redisTemplate.opsForSet().remove(key, value);
        logger.debug("Removed value from set for key: {}", key);
    }

    @Override
    public Set<String> getSet(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    @Override
    public boolean isInSet(String key, String value) {
        Boolean isMember = redisTemplate.opsForSet().isMember(key, value);
        return Boolean.TRUE.equals(isMember);
    }

    @Override
    public void addToList(String key, String value) {
        redisTemplate.opsForList().rightPush(key, value);
        logger.debug("Added value to list for key: {}", key);
    }

    @Override
    public List<String> getList(String key) {
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    @Override
    public List<String> getList(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    @Override
    public void trimList(String key, long start, long end) {
        redisTemplate.opsForList().trim(key, start, end);
        logger.debug("Trimmed list for key: {} to range [{}, {}]", key, start, end);
    }

    @Override
    public void putHash(String key, String field, String value) {
        redisTemplate.opsForHash().put(key, field, value);
        logger.debug("Put hash field {} for key: {}", field, key);
    }

    @Override
    public String getHash(String key, String field) {
        Object value = redisTemplate.opsForHash().get(key, field);
        return value != null ? value.toString() : null;
    }

    @Override
    public void deleteHash(String key, String field) {
        redisTemplate.opsForHash().delete(key, field);
        logger.debug("Deleted hash field {} for key: {}", field, key);
    }

    @Override
    public Set<String> getHashKeys(String key) {
        Set<Object> keys = redisTemplate.opsForHash().keys(key);
        return keys.stream()
            .map(Object::toString)
            .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public void clear() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        logger.warn("Cleared all cache entries");
    }

    @Override
    public long size() {
        return redisTemplate.getConnectionFactory().getConnection().dbSize();
    }
}