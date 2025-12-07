package com.marketplace.search.catalog.domain.valueobjects;

import java.time.Instant;

import jakarta.validation.constraints.Min;

/**
 * Value Object representando métricas de performance do produto
 */
public record ProductMetrics(
    @Min(0) int totalViews,
    @Min(0) int totalSales,
    @Min(0) int totalReviews,
    @Min(0) double averageRating,
    @Min(0) int stockQuantity,
    double conversionRate,
    Instant lastSale,
    Instant lastView) {
  public ProductMetrics {
    if (totalViews < 0) {
      throw new IllegalArgumentException("total views cannot be negative");
    }
    if (totalSales < 0) {
      throw new IllegalArgumentException("total sales cannot be negative");
    }
    if (totalReviews < 0) {
      throw new IllegalArgumentException("total reviews cannot be negative");
    }
    if (averageRating < 0.0 || averageRating > 5.0) {
      throw new IllegalArgumentException("Average rating must be between 0.0 and 5.0");
    }
    if (stockQuantity < 0) {
      throw new IllegalArgumentException("stock quantity cannot be negative");
    }
    if (conversionRate < 0.0 || conversionRate > 1.0) {
      throw new IllegalArgumentException("Conversion rate must be between 0.0 and 1.0");
    }
  }

  /**
   * Calcula o score de popularidade (0.0 a 1.0)
   */
  public double getPopularityScore() {
    double salesScore = Math.min(1.0, totalSales / 1000.0); // Normaliza até 1000 vendas
    double viewsScore = Math.min(1.0, totalViews / 10000.0); // Normaliza até 10k visualizações
    double ratingScore = totalReviews > 0 ? averageRating / 5.0 : 0.0;
    double conversionScore = conversionRate;

    // Boost para produtos com atividade recente
    double recencyBoost = calculateRecencyBoost();

    double finalScore = (salesScore * 0.4) +
        (viewsScore * 0.2) +
        (ratingScore * 0.3) +
        (conversionScore * 0.1) +
        recencyBoost;

    return Math.max(0.0, Math.min(1.0, finalScore));
  }

  private double calculateRecencyBoost() {
    if (lastSale == null && lastView == null) {
      return 0.0;
    }

    Instant mostRecent = lastSale != null && lastView != null ? (lastSale.isAfter(lastView) ? lastSale : lastView)
        : (lastSale != null ? lastSale : lastView);

    long daysSinceActivity = java.time.Duration.between(mostRecent, Instant.now()).toDays();

    if (daysSinceActivity <= 1)
      return 0.1;
    if (daysSinceActivity <= 7)
      return 0.05;
    if (daysSinceActivity <= 30)
      return 0.02;

    return 0.0;
  }

  public boolean isPopular() {
    return totalSales > 100 && averageRating >= 4.0 && conversionRate > 0.1;
  }

  public boolean isInStock() {
    return stockQuantity > 0;
  }

  public boolean isHighlyRated() {
    return totalReviews >= 10 && averageRating >= 4.5;
  }

  @Override
  public String toString() {
    return "ProductMetrics{" +
        "totalViews=" + totalViews +
        ", totalSales=" + totalSales +
        ", averageRating=" + averageRating +
        ", stockQuantity=" + stockQuantity +
        '}';
  }
}