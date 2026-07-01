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
    public boolean tryAcquireLock(String key, Duration waitTime, Duration leaseTime) {
        long start = System.currentTimeMillis();
        long maxWait = waitTime.toMillis();
        String lockKey = LOCK_PREFIX + key;
        
        while (System.currentTimeMillis() - start < maxWait) {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", leaseTime);
            if (success != null && success) {
                logger.debug("Lock adquirido para chave (com espera): {}", lockKey);
                return true;
            }
            try {
                // Spin lock: aguarda 100ms antes de tentar novamente
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Thread interrompida enquanto aguardava o lock para a chave: {}", lockKey);
                return false;
            }
        }
        
        logger.trace("Falha ao adquirir lock para chave (timeout esgotado): {}", lockKey);
        return false;
    }

    @Override
    public void releaseLock(String key) {
        String lockKey = LOCK_PREFIX + key;
        redisTemplate.delete(lockKey);
        logger.debug("Lock liberado para chave: {}", lockKey);
    }
}
