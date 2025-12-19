package com.marketplace.search.search.application.queries;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SearchMetricsData(
    @JsonProperty("queries_per_second") int queriesPerSecond,

    @JsonProperty("average_score") double averageScore,

    @JsonProperty("indexed_documents") int indexedDocuments,

    @JsonProperty("index_size") long indexSize,

    @JsonProperty("used_cache") boolean usedCache,

    @JsonProperty("shard_info") String shardInfo) {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private int queriesPerSecond;
    private double averageScore;
    private int indexedDocuments;
    private long indexSize;
    private boolean usedCache;
    private String shardInfo;

    public Builder queriesPerSecond(int queriesPerSecond) {
      this.queriesPerSecond = queriesPerSecond;
      return this;
    }

    public Builder averageScore(double averageScore) {
      this.averageScore = averageScore;
      return this;
    }

    public Builder indexedDocuments(int indexedDocuments) {
      this.indexedDocuments = indexedDocuments;
      return this;
    }

    public Builder indexSize(long indexSize) {
      this.indexSize = indexSize;
      return this;
    }

    public Builder usedCache(boolean usedCache) {
      this.usedCache = usedCache;
      return this;
    }

    public Builder shardInfo(String shardInfo) {
      this.shardInfo = shardInfo;
      return this;
    }

    public SearchMetricsData build() {
      return new SearchMetricsData(queriesPerSecond, averageScore, indexedDocuments,
          indexSize, usedCache, shardInfo);
    }
  }

}

