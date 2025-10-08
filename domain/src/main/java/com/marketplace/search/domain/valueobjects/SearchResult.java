package com.marketplace.search.domain.valueobjects;

import com.marketplace.search.domain.entities.Product;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Value Object representando o resultado de uma busca
 */
public class SearchResult {
    
    private final List<Product> products;
    
    private final long totalCount;
    
    private final int pageSize;
    
    private final int pageNumber;
    
    private final Duration executionTime;
    
    private final SearchMetrics metrics;

    public SearchResult(List<Product> products, long totalCount, int pageSize, 
                       int pageNumber, Duration executionTime, SearchMetrics metrics) {
        this.products = Objects.requireNonNull(products, "Products cannot be null");
        this.totalCount = validateTotalCount(totalCount);
        this.pageSize = validatePageSize(pageSize);
        this.pageNumber = validatePageNumber(pageNumber);
        this.executionTime = Objects.requireNonNull(executionTime, "Execution time cannot be null");
        this.metrics = metrics;
    }

    private long validateTotalCount(long totalCount) {
        if (totalCount < 0) {
            throw new IllegalArgumentException("Total count cannot be negative");
        }
        return totalCount;
    }

    private int validatePageSize(int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be positive");
        }
        return pageSize;
    }

    private int validatePageNumber(int pageNumber) {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("Page number cannot be negative");
        }
        return pageNumber;
    }

    /**
     * Verifica se há resultados
     */
    public boolean hasResults() {
        return !products.isEmpty();
    }

    /**
     * Verifica se há mais páginas disponíveis
     */
    public boolean hasNextPage() {
        return (long) (pageNumber + 1) * pageSize < totalCount;
    }

    /**
     * Verifica se há páginas anteriores
     */
    public boolean hasPreviousPage() {
        return pageNumber > 0;
    }

    /**
     * Calcula o total de páginas
     */
    public int getTotalPages() {
        return (int) Math.ceil((double) totalCount / pageSize);
    }

    /**
     * Obtém o número da próxima página (se existir)
     */
    public Integer getNextPageNumber() {
        return hasNextPage() ? pageNumber + 1 : null;
    }

    /**
     * Obtém o número da página anterior (se existir)
     */
    public Integer getPreviousPageNumber() {
        return hasPreviousPage() ? pageNumber - 1 : null;
    }

    /**
     * Verifica se a busca foi executada rapidamente (< 100ms)
     */
    public boolean isFastExecution() {
        return executionTime.toMillis() < 100;
    }

    // Getters
    public List<Product> getProducts() { return products; }
    public long getTotalCount() { return totalCount; }
    public int getPageSize() { return pageSize; }
    public int getPageNumber() { return pageNumber; }
    public Duration getExecutionTime() { return executionTime; }
    public SearchMetrics getMetrics() { return metrics; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchResult that = (SearchResult) o;
        return totalCount == that.totalCount &&
               pageSize == that.pageSize &&
               pageNumber == that.pageNumber &&
               Objects.equals(products, that.products) &&
               Objects.equals(executionTime, that.executionTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(products, totalCount, pageSize, pageNumber, executionTime);
    }

    @Override
    public String toString() {
        return "SearchResult{" +
                "resultCount=" + products.size() +
                ", totalCount=" + totalCount +
                ", pageNumber=" + pageNumber +
                ", executionTime=" + executionTime.toMillis() + "ms" +
                '}';
    }
}