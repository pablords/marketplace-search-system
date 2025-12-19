package com.marketplace.search.search.domain.valueobjects;

import java.time.Duration;
import java.util.List;

import com.marketplace.search.search.domain.entities.Product;

/**
 * Value Object representando o resultado de uma busca
 */
public record SearchResult(
    List<Product> products,
    long totalCount,
    int pageSize,
    int pageNumber,
    Duration executionTime,
    SearchMetrics metrics
) {
    public SearchResult {
        if (products == null) {
            throw new IllegalArgumentException("Products cannot be null");
        }
        if (totalCount < 0) {
            throw new IllegalArgumentException("Total count cannot be negative");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be positive");
        }
        if (pageNumber < 0) {
            throw new IllegalArgumentException("Page number cannot be negative");
        }
        if (executionTime == null) {
            throw new IllegalArgumentException("Execution time cannot be null");
        }
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

