package com.marketplace.search.domain.valueobjects;

import java.util.Objects;

/**
 * Value Object contendo métricas da execução da busca
 */
public class SearchMetrics {
    
    private final int queriesPerSecond;
    
    private final double averageScore;
    
    private final int indexedDocuments;
    
    private final long indexSize;
    
    private final boolean usedCache;
    
    private final String shardInfo;

    public SearchMetrics(int queriesPerSecond, double averageScore, int indexedDocuments,
                        long indexSize, boolean usedCache, String shardInfo) {
        this.queriesPerSecond = validateQps(queriesPerSecond);
        this.averageScore = validateScore(averageScore);
        this.indexedDocuments = validateIndexedDocuments(indexedDocuments);
        this.indexSize = validateIndexSize(indexSize);
        this.usedCache = usedCache;
        this.shardInfo = shardInfo;
    }

    private int validateQps(int qps) {
        if (qps < 0) {
            throw new IllegalArgumentException("QPS cannot be negative");
        }
        return qps;
    }

    private double validateScore(double score) {
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("Average score must be between 0.0 and 1.0");
        }
        return score;
    }

    private int validateIndexedDocuments(int documents) {
        if (documents < 0) {
            throw new IllegalArgumentException("Indexed documents cannot be negative");
        }
        return documents;
    }

    private long validateIndexSize(long size) {
        if (size < 0) {
            throw new IllegalArgumentException("Index size cannot be negative");
        }
        return size;
    }

    public static SearchMetrics empty() {
        return new SearchMetrics(0, 0.0, 0, 0, false, null);
    }

    public boolean isHighLoad() {
        return queriesPerSecond > 1000;
    }

    public boolean hasGoodQuality() {
        return averageScore > 0.7;
    }

    // Getters
    public int getQueriesPerSecond() { return queriesPerSecond; }
    public double getAverageScore() { return averageScore; }
    public int getIndexedDocuments() { return indexedDocuments; }
    public long getIndexSize() { return indexSize; }
    public boolean isUsedCache() { return usedCache; }
    public String getShardInfo() { return shardInfo; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchMetrics that = (SearchMetrics) o;
        return queriesPerSecond == that.queriesPerSecond &&
               Double.compare(that.averageScore, averageScore) == 0 &&
               indexedDocuments == that.indexedDocuments &&
               indexSize == that.indexSize &&
               usedCache == that.usedCache;
    }

    @Override
    public int hashCode() {
        return Objects.hash(queriesPerSecond, averageScore, indexedDocuments, indexSize, usedCache);
    }

    @Override
    public String toString() {
        return "SearchMetrics{" +
                "qps=" + queriesPerSecond +
                ", avgScore=" + averageScore +
                ", documents=" + indexedDocuments +
                ", cache=" + usedCache +
                '}';
    }
}