package com.marketplace.search.catalog.infrastructure.persistence.redis;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import com.marketplace.search.catalog.domain.ports.DistributedLockPort;

/**
 * Implementação do DistributedLockPort usando Redis.
 * Utiliza a operação SETNX (Set if Not Exists) do Redis para garantir atomicidade.
 */
@Component
public class RedisDistributedLockAdapter implements DistributedLockPort {

    private static final Logger logger = LoggerFactory.getLogger(RedisDistributedLockAdapter.class);
    private static final String LOCK_PREFIX = "lock:product:";
    
    private final StringRedisTemplate redisTemplate;

    public RedisDistributedLockAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean acquireLock(String key, Duration duration) {
        String lockKey = LOCK_PREFIX + key;
        
        // SET key value NX EX duration
        // NX: apenas se não existir
        // EX: expiração automática
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", duration);
        
        boolean acquired = success != null && success;
        
        if (acquired) {
            logger.debug("Lock adquirido para chave: {}", lockKey);
        } else {
            logger.trace("Falha ao adquirir lock para chave: {}", lockKey);
        }
        
        return acquired;
    }

    @Override
    public void releaseLock(String key) {
        String lockKey = LOCK_PREFIX + key;
        redisTemplate.delete(lockKey);
        logger.debug("Lock liberado para chave: {}", lockKey);
    }
}
