package com.marketplace.search.indexing.application.services;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.marketplace.search.indexing.domain.repositories.CacheRepository;

/**
 * Serviço responsável por garantir idempotência no processamento de eventos Kafka.
 * Usa Redis para rastrear eventos já processados e evitar duplicação.
 * 
 * Estratégia de deduplicação:
 * - Chave única: event:processed:{productId}:{timestamp}:{offset}
 * - Usa SETNX (set if not exists) para verificação atômica
 * - TTL configurável (padrão: 7 dias) para limpeza automática
 */
@Service
public class EventDeduplicationService {

    private static final Logger logger = LoggerFactory.getLogger(EventDeduplicationService.class);
    
    private static final String KEY_PREFIX = "event:processed:";
    
    private final CacheRepository cacheRepository;
    
    @Value("${kafka.deduplication.ttl-hours:168}") // Padrão: 7 dias (168 horas)
    private long ttlHours;

    public EventDeduplicationService(CacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    /**
     * Verifica se um evento já foi processado e marca como processado se não foi.
     * 
     * @param productId ID do produto
     * @param timestamp Timestamp do evento (ts_ms do Debezium)
     * @param offset Offset do Kafka
     * @return true se o evento já foi processado (duplicado), false se é novo
     */
    public boolean isDuplicate(String productId, Long timestamp, Long offset) {
        String eventKey = buildEventKey(productId, timestamp, offset);
        
        // Verifica se a chave já existe (evento já processado)
        if (cacheRepository.exists(eventKey)) {
            logger.debug("Evento duplicado detectado: productId={}, timestamp={}, offset={}", 
                productId, timestamp, offset);
            return true;
        }
        
        // Marca o evento como processado usando SETNX (atomic)
        // Armazena timestamp atual como valor para auditoria
        String eventValue = String.valueOf(System.currentTimeMillis());
        Duration ttl = Duration.ofHours(ttlHours);
        cacheRepository.put(eventKey, eventValue, ttl);
        
        logger.debug("Evento marcado como processado: productId={}, timestamp={}, offset={}, ttl={}h", 
            productId, timestamp, offset, ttlHours);
        
        return false;
    }

    /**
     * Verifica se um evento já foi processado sem marcá-lo.
     * Útil para verificação apenas.
     * 
     * @param productId ID do produto
     * @param timestamp Timestamp do evento
     * @param offset Offset do Kafka
     * @return true se o evento já foi processado
     */
    public boolean wasProcessed(String productId, Long timestamp, Long offset) {
        String eventKey = buildEventKey(productId, timestamp, offset);
        return cacheRepository.exists(eventKey);
    }

    /**
     * Marca um evento como processado explicitamente.
     * 
     * @param productId ID do produto
     * @param timestamp Timestamp do evento
     * @param offset Offset do Kafka
     */
    public void markAsProcessed(String productId, Long timestamp, Long offset) {
        String eventKey = buildEventKey(productId, timestamp, offset);
        String eventValue = String.valueOf(System.currentTimeMillis());
        Duration ttl = Duration.ofHours(ttlHours);
        
        cacheRepository.put(eventKey, eventValue, ttl);
        
        logger.debug("Evento marcado como processado explicitamente: productId={}, timestamp={}, offset={}", 
            productId, timestamp, offset);
    }

    /**
     * Constrói a chave única para um evento.
     * Formato: event:processed:{productId}:{timestamp}:{offset}
     */
    private String buildEventKey(String productId, Long timestamp, Long offset) {
        return KEY_PREFIX + productId + ":" + timestamp + ":" + offset;
    }

    /**
     * Remove a marcação de um evento (útil para testes ou reprocessamento manual).
     * 
     * @param productId ID do produto
     * @param timestamp Timestamp do evento
     * @param offset Offset do Kafka
     */
    public void unmarkAsProcessed(String productId, Long timestamp, Long offset) {
        String eventKey = buildEventKey(productId, timestamp, offset);
        cacheRepository.evict(eventKey);
        
        logger.debug("Marcação de evento removida: productId={}, timestamp={}, offset={}", 
            productId, timestamp, offset);
    }
}

