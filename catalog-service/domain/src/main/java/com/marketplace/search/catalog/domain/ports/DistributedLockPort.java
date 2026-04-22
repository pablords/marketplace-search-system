package com.marketplace.search.catalog.domain.ports;

import java.time.Duration;

/**
 * Port para gerenciamento de locks distribuídos.
 * Permite garantir que apenas um processo manipule um recurso específico por vez em todo o cluster.
 */
public interface DistributedLockPort {

    /**
     * Tenta adquirir um lock para a chave informada.
     * 
     * @param key Chave do recurso a ser travado
     * @param duration Tempo de expiração do lock (TTL) para evitar deadlocks se o processo cair
     * @return true se o lock foi adquirido, false caso contrário
     */
    boolean acquireLock(String key, Duration duration);

    /**
     * Libera o lock para a chave informada.
     * 
     * @param key Chave do recurso a ser destravado
     */
    void releaseLock(String key);
}
