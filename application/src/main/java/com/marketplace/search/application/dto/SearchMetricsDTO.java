package com.marketplace.search.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO para métricas de busca
 */
public class SearchMetricsDTO {
    
    @JsonProperty("queries_per_second")
    private int queriesPerSecond;
    
    @JsonProperty("average_score")
    private double averageScore;
    
    @JsonProperty("indexed_documents")
    private int indexedDocuments;
    
    @JsonProperty("index_size")
    private long indexSize;
    
    @JsonProperty("used_cache")
    private boolean usedCache;
    
    @JsonProperty("shard_info")
    private String shardInfo;

    // Constructors
    public SearchMetricsDTO() {}

    // Getters and Setters
    public int getQueriesPerSecond() { return queriesPerSecond; }
    public void setQueriesPerSecond(int queriesPerSecond) { this.queriesPerSecond = queriesPerSecond; }

    public double getAverageScore() { return averageScore; }
    public void setAverageScore(double averageScore) { this.averageScore = averageScore; }

    public int getIndexedDocuments() { return indexedDocuments; }
    public void setIndexedDocuments(int indexedDocuments) { this.indexedDocuments = indexedDocuments; }

    public long getIndexSize() { return indexSize; }
    public void setIndexSize(long indexSize) { this.indexSize = indexSize; }

    public boolean isUsedCache() { return usedCache; }
    public void setUsedCache(boolean usedCache) { this.usedCache = usedCache; }

    public String getShardInfo() { return shardInfo; }
    public void setShardInfo(String shardInfo) { this.shardInfo = shardInfo; }

    @Override
    public String toString() {
        return "SearchMetricsDTO{" +
                "qps=" + queriesPerSecond +
                ", avgScore=" + averageScore +
                ", documents=" + indexedDocuments +
                ", cache=" + usedCache +
                '}';
    }
}