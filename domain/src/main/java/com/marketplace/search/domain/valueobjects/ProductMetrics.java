package com.marketplace.search.domain.valueobjects;

import jakarta.validation.constraints.Min;

import java.time.Instant;
import java.util.Objects;

/**
 * Value Object representando métricas de performance do produto
 */
public class ProductMetrics {
    
    @Min(0)
    private final int totalViews;
    
    @Min(0)
    private final int totalSales;
    
    @Min(0)
    private final int totalReviews;
    
    @Min(0)
    private final double averageRating; // 0.0 a 5.0
    
    @Min(0)
    private final int stockQuantity;
    
    private final double conversionRate; // 0.0 a 1.0
    
    private final Instant lastSale;
    
    private final Instant lastView;

    public ProductMetrics(int totalViews, int totalSales, int totalReviews,
                         double averageRating, int stockQuantity, double conversionRate,
                         Instant lastSale, Instant lastView) {
        this.totalViews = validateNonNegative(totalViews, "total views");
        this.totalSales = validateNonNegative(totalSales, "total sales");
        this.totalReviews = validateNonNegative(totalReviews, "total reviews");
        this.averageRating = validateRating(averageRating);
        this.stockQuantity = validateNonNegative(stockQuantity, "stock quantity");
        this.conversionRate = validateRate(conversionRate);
        this.lastSale = lastSale;
        this.lastView = lastView;
    }

    private int validateNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
        return value;
    }

    private double validateRating(double rating) {
        if (rating < 0.0 || rating > 5.0) {
            throw new IllegalArgumentException("Average rating must be between 0.0 and 5.0");
        }
        return rating;
    }

    private double validateRate(double rate) {
        if (rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException("Conversion rate must be between 0.0 and 1.0");
        }
        return rate;
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
        
        Instant mostRecent = lastSale != null && lastView != null ? 
            (lastSale.isAfter(lastView) ? lastSale : lastView) :
            (lastSale != null ? lastSale : lastView);
        
        long daysSinceActivity = java.time.Duration.between(mostRecent, Instant.now()).toDays();
        
        if (daysSinceActivity <= 1) return 0.1;
        if (daysSinceActivity <= 7) return 0.05;
        if (daysSinceActivity <= 30) return 0.02;
        
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

    // Getters
    public int getTotalViews() { return totalViews; }
    public int getTotalSales() { return totalSales; }
    public int getTotalReviews() { return totalReviews; }
    public double getAverageRating() { return averageRating; }
    public int getStockQuantity() { return stockQuantity; }
    public double getConversionRate() { return conversionRate; }
    public Instant getLastSale() { return lastSale; }
    public Instant getLastView() { return lastView; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductMetrics that = (ProductMetrics) o;
        return totalViews == that.totalViews &&
               totalSales == that.totalSales &&
               totalReviews == that.totalReviews &&
               Double.compare(that.averageRating, averageRating) == 0 &&
               stockQuantity == that.stockQuantity &&
               Double.compare(that.conversionRate, conversionRate) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalViews, totalSales, totalReviews, averageRating, 
                          stockQuantity, conversionRate);
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