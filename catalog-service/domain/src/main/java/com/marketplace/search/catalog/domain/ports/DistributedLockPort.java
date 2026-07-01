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
     * Tenta adquirir um lock para a chave informada, aguardando até waitTime caso o lock esteja em uso.
     * 
     * @param key Chave do recurso a ser travado
     * @param waitTime Tempo máximo para aguardar a liberação do lock
     * @param leaseTime Tempo de expiração do lock (TTL) para evitar deadlocks
     * @return true se o lock foi adquirido, false se o waitTime esgotou
     */
    boolean tryAcquireLock(String key, Duration waitTime, Duration leaseTime);

    /**
     * Libera o lock para a chave informada.
     * 
     * @param key Chave do recurso a ser destravado
     */
    void releaseLock(String key);
}
