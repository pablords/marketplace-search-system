package com.marketplace.search.catalog.domain.valueobjects;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Objects;

/**
 * Value Object representando a reputação de um vendedor
 */
public class SellerReputation {
    
    @Min(0)
    @Max(5)
    private final double score; // 0.0 a 5.0
    
    private final int totalReviews;
    
    private final int positiveReviews;
    
    private final int neutralReviews;
    
    private final int negativeReviews;
    
    private final double cancellationRate; // 0.0 a 1.0
    
    private final double deliveryPerformance; // 0.0 a 1.0

    public SellerReputation(double score, int totalReviews, int positiveReviews,
                           int neutralReviews, int negativeReviews,
                           double cancellationRate, double deliveryPerformance) {
        this.score = validateScore(score);
        this.totalReviews = validateTotalReviews(totalReviews);
        this.positiveReviews = validatePositiveReviews(positiveReviews);
        this.neutralReviews = validateNeutralReviews(neutralReviews);
        this.negativeReviews = validateNegativeReviews(negativeReviews);
        this.cancellationRate = validateRate(cancellationRate, "cancellation rate");
        this.deliveryPerformance = validateRate(deliveryPerformance, "delivery performance");
        
        validateReviewsSum();
    }

    private double validateScore(double score) {
        if (score < 0.0 || score > 5.0) {
            throw new IllegalArgumentException("Score must be between 0.0 and 5.0");
        }
        return score;
    }

    private int validateTotalReviews(int totalReviews) {
        if (totalReviews < 0) {
            throw new IllegalArgumentException("Total reviews cannot be negative");
        }
        return totalReviews;
    }

    private int validatePositiveReviews(int positiveReviews) {
        if (positiveReviews < 0) {
            throw new IllegalArgumentException("Positive reviews cannot be negative");
        }
        return positiveReviews;
    }

    private int validateNeutralReviews(int neutralReviews) {
        if (neutralReviews < 0) {
            throw new IllegalArgumentException("Neutral reviews cannot be negative");
        }
        return neutralReviews;
    }

    private int validateNegativeReviews(int negativeReviews) {
        if (negativeReviews < 0) {
            throw new IllegalArgumentException("Negative reviews cannot be negative");
        }
        return negativeReviews;
    }

    private double validateRate(double rate, String fieldName) {
        if (rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
        return rate;
    }

    private void validateReviewsSum() {
        if (positiveReviews + neutralReviews + negativeReviews != totalReviews) {
            throw new IllegalArgumentException("Sum of review types must equal total reviews");
        }
    }

    /**
     * Calcula score normalizado (0.0 a 1.0) baseado em múltiplos fatores
     */
    public double getNormalizedScore() {
        if (totalReviews == 0) {
            return 0.5; // Score neutro para vendedores sem avaliações
        }
        
        // Score base normalizado (0-5 para 0-1)
        double normalizedScore = score / 5.0;
        
        // Ajuste baseado na performance de entrega
        normalizedScore *= deliveryPerformance;
        
        // Penalidade por alta taxa de cancelamento
        normalizedScore *= (1.0 - cancellationRate);
        
        // Confiabilidade baseada no número de avaliações
        double reliabilityFactor = Math.min(1.0, totalReviews / 100.0);
        normalizedScore *= reliabilityFactor;
        
        return Math.max(0.0, Math.min(1.0, normalizedScore));
    }

    public boolean isHighQuality() {
        return score >= 4.5 && 
               totalReviews >= 50 && 
               cancellationRate <= 0.05 && 
               deliveryPerformance >= 0.95;
    }

    public boolean isLowQuality() {
        return score < 3.0 || 
               cancellationRate > 0.15 || 
               deliveryPerformance < 0.8;
    }

    // Getters
    public double getScore() { return score; }
    public int getTotalReviews() { return totalReviews; }
    public int getPositiveReviews() { return positiveReviews; }
    public int getNeutralReviews() { return neutralReviews; }
    public int getNegativeReviews() { return negativeReviews; }
    public double getCancellationRate() { return cancellationRate; }
    public double getDeliveryPerformance() { return deliveryPerformance; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SellerReputation that = (SellerReputation) o;
        return Double.compare(that.score, score) == 0 &&
               totalReviews == that.totalReviews &&
               positiveReviews == that.positiveReviews &&
               neutralReviews == that.neutralReviews &&
               negativeReviews == that.negativeReviews &&
               Double.compare(that.cancellationRate, cancellationRate) == 0 &&
               Double.compare(that.deliveryPerformance, deliveryPerformance) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(score, totalReviews, positiveReviews, neutralReviews, 
                          negativeReviews, cancellationRate, deliveryPerformance);
    }

    @Override
    public String toString() {
        return "SellerReputation{" +
                "score=" + score +
                ", totalReviews=" + totalReviews +
                ", cancellationRate=" + cancellationRate +
                ", deliveryPerformance=" + deliveryPerformance +
                '}';
    }
}