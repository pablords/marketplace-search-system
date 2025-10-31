
package com.marketplace.search.domain.valueobjects;

import java.time.Instant;
import java.util.Objects;

/**
 * Value Object contendo informações sobre o índice de busca
 */
public record IndexInfo(
    String indexName,
    long documentCount,
    long sizeInBytes,
    IndexStatus status,
    Instant lastUpdated,
    Instant lastOptimized,
    int shardCount,
    int replicaCount
) {
    public IndexInfo {
        Objects.requireNonNull(indexName, "Index name cannot be null");
        if (documentCount < 0) throw new IllegalArgumentException("Document count cannot be negative");
        if (sizeInBytes < 0) throw new IllegalArgumentException("Size cannot be negative");
        Objects.requireNonNull(status, "Status cannot be null");
        if (shardCount <= 0) throw new IllegalArgumentException("Shard count must be positive");
        if (replicaCount < 0) throw new IllegalArgumentException("Replica count cannot be negative");
    }

    /**
     * Obtém o tamanho do índice em MB
     */
    public double getSizeInMB() {
        return sizeInBytes / (1024.0 * 1024.0);
    }

    /**
     * Obtém o tamanho do índice em GB
     */
    public double getSizeInGB() {
        return sizeInBytes / (1024.0 * 1024.0 * 1024.0);
    }

    /**
     * Verifica se o índice está saudável
     */
    public boolean isHealthy() {
        return status == IndexStatus.GREEN || status == IndexStatus.YELLOW;
    }

    /**
     * Verifica se precisa de otimização
     */
    public boolean needsOptimization() {
        if (lastOptimized == null) return true;
        long daysSinceOptimization = java.time.Duration.between(lastOptimized, Instant.now()).toDays();
        return daysSinceOptimization > 7; // Otimizar semanalmente
    }

    @Override
    public String toString() {
        return "IndexInfo{" +
                "name='" + indexName + '\'' +
                ", documents=" + documentCount +
                ", size=" + String.format("%.2f MB", getSizeInMB()) +
                ", status=" + status +
                ", shards=" + shardCount +
                '}';
    }
}

enum IndexStatus {
    GREEN,   // Todos os shards estão funcionando
    YELLOW,  // Shards primários funcionando, alguns replicas indisponíveis
    RED      // Alguns shards primários indisponíveis
}