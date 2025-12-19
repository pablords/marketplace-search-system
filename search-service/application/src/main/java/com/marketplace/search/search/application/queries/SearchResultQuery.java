package com.marketplace.search.search.application.queries;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SearchResultQuery(
    @JsonProperty("products") List<ProductData> products,

    @JsonProperty("total_count") long totalCount,

    @JsonProperty("page_size") int pageSize,

    @JsonProperty("page_number") int pageNumber,

    @JsonProperty("total_pages") int totalPages,

    @JsonProperty("has_next_page") boolean hasNextPage,

    @JsonProperty("has_previous_page") boolean hasPreviousPage,

    @JsonProperty("execution_time_ms") long executionTimeMs,

    @JsonProperty("metrics") SearchMetricsData metrics) {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private List<ProductData> products;
    private long totalCount;
    private int pageSize;
    private int pageNumber;
    private int totalPages;
    private boolean hasNextPage;
    private boolean hasPreviousPage;
    private long executionTimeMs;
    private SearchMetricsData metrics;

    public Builder products(List<ProductData> products) {
      this.products = products;
      return this;
    }

    public Builder totalCount(long totalCount) {
      this.totalCount = totalCount;
      return this;
    }

    public Builder pageSize(int pageSize) {
      this.pageSize = pageSize;
      return this;
    }

    public Builder pageNumber(int pageNumber) {
      this.pageNumber = pageNumber;
      return this;
    }

    public Builder totalPages(int totalPages) {
      this.totalPages = totalPages;
      return this;
    }

    public Builder hasNextPage(boolean hasNextPage) {
      this.hasNextPage = hasNextPage;
      return this;
    }

    public Builder hasPreviousPage(boolean hasPreviousPage) {
      this.hasPreviousPage = hasPreviousPage;
      return this;
    }

    public Builder executionTimeMs(long executionTimeMs) {
      this.executionTimeMs = executionTimeMs;
      return this;
    }

    public Builder metrics(SearchMetricsData metrics) {
      this.metrics = metrics;
      return this;
    }

    public SearchResultQuery build() {
      return new SearchResultQuery(products, totalCount, pageSize, pageNumber,
          totalPages, hasNextPage, hasPreviousPage,
          executionTimeMs, metrics);
    }
  }

}

