package com.marketplace.search.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO para resposta de busca
 */
public class SearchResultDTO {
    
    @JsonProperty("products")
    private List<ProductDTO> products;
    
    @JsonProperty("total_count")
    private long totalCount;
    
    @JsonProperty("page_size")
    private int pageSize;
    
    @JsonProperty("page_number")
    private int pageNumber;
    
    @JsonProperty("total_pages")
    private int totalPages;
    
    @JsonProperty("has_next_page")
    private boolean hasNextPage;
    
    @JsonProperty("has_previous_page")
    private boolean hasPreviousPage;
    
    @JsonProperty("execution_time_ms")
    private long executionTimeMs;
    
    @JsonProperty("metrics")
    private SearchMetricsDTO metrics;

    // Constructors
    public SearchResultDTO() {}

    // Getters and Setters
    public List<ProductDTO> getProducts() { return products; }
    public void setProducts(List<ProductDTO> products) { this.products = products; }

    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public boolean isHasNextPage() { return hasNextPage; }
    public void setHasNextPage(boolean hasNextPage) { this.hasNextPage = hasNextPage; }

    public boolean isHasPreviousPage() { return hasPreviousPage; }
    public void setHasPreviousPage(boolean hasPreviousPage) { this.hasPreviousPage = hasPreviousPage; }

    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public SearchMetricsDTO getMetrics() { return metrics; }
    public void setMetrics(SearchMetricsDTO metrics) { this.metrics = metrics; }

    @Override
    public String toString() {
        return "SearchResultDTO{" +
                "resultCount=" + (products != null ? products.size() : 0) +
                ", totalCount=" + totalCount +
                ", pageNumber=" + pageNumber +
                ", executionTimeMs=" + executionTimeMs +
                '}';
    }
}