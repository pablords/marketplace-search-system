package com.marketplace.search.domain.valueobjects;

import java.time.Instant;
import java.util.Objects;

/**
 * Value Object contendo informações sobre o índice de busca
 */
public class IndexInfo {
    
    private final String indexName;
    
    private final long documentCount;
    
    private final long sizeInBytes;
    
    private final IndexStatus status;
    
    private final Instant lastUpdated;
    
    private final Instant lastOptimized;
    
    private final int shardCount;
    
    private final int replicaCount;

    public IndexInfo(String indexName, long documentCount, long sizeInBytes,
                    IndexStatus status, Instant lastUpdated, Instant lastOptimized,
                    int shardCount, int replicaCount) {
        this.indexName = Objects.requireNonNull(indexName, "Index name cannot be null");
        this.documentCount = validateDocumentCount(documentCount);
        this.sizeInBytes = validateSize(sizeInBytes);
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.lastUpdated = lastUpdated;
        this.lastOptimized = lastOptimized;
        this.shardCount = validateShardCount(shardCount);
        this.replicaCount = validateReplicaCount(replicaCount);
    }

    private long validateDocumentCount(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("Document count cannot be negative");
        }
        return count;
    }

    private long validateSize(long size) {
        if (size < 0) {
            throw new IllegalArgumentException("Size cannot be negative");
        }
        return size;
    }

    private int validateShardCount(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Shard count must be positive");
        }
        return count;
    }

    private int validateReplicaCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Replica count cannot be negative");
        }
        return count;
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

    // Getters
    public String getIndexName() { return indexName; }
    public long getDocumentCount() { return documentCount; }
    public long getSizeInBytes() { return sizeInBytes; }
    public IndexStatus getStatus() { return status; }
    public Instant getLastUpdated() { return lastUpdated; }
    public Instant getLastOptimized() { return lastOptimized; }
    public int getShardCount() { return shardCount; }
    public int getReplicaCount() { return replicaCount; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IndexInfo indexInfo = (IndexInfo) o;
        return Objects.equals(indexName, indexInfo.indexName) &&
               documentCount == indexInfo.documentCount &&
               status == indexInfo.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(indexName, documentCount, status);
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