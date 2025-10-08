package com.marketplace.search.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO para reputação do vendedor
 */
public class SellerReputationDTO {
    
    @JsonProperty("score")
    private Double score;
    
    @JsonProperty("total_reviews")
    private Integer totalReviews;
    
    @JsonProperty("positive_reviews")
    private Integer positiveReviews;
    
    @JsonProperty("neutral_reviews")
    private Integer neutralReviews;
    
    @JsonProperty("negative_reviews")
    private Integer negativeReviews;
    
    @JsonProperty("cancellation_rate")
    private Double cancellationRate;
    
    @JsonProperty("delivery_performance")
    private Double deliveryPerformance;

    // Constructors
    public SellerReputationDTO() {}

    public SellerReputationDTO(Double score, Integer totalReviews, Integer positiveReviews,
                              Integer neutralReviews, Integer negativeReviews,
                              Double cancellationRate, Double deliveryPerformance) {
        this.score = score;
        this.totalReviews = totalReviews;
        this.positiveReviews = positiveReviews;
        this.neutralReviews = neutralReviews;
        this.negativeReviews = negativeReviews;
        this.cancellationRate = cancellationRate;
        this.deliveryPerformance = deliveryPerformance;
    }

    // Getters and Setters
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public Integer getTotalReviews() { return totalReviews; }
    public void setTotalReviews(Integer totalReviews) { this.totalReviews = totalReviews; }

    public Integer getPositiveReviews() { return positiveReviews; }
    public void setPositiveReviews(Integer positiveReviews) { this.positiveReviews = positiveReviews; }

    public Integer getNeutralReviews() { return neutralReviews; }
    public void setNeutralReviews(Integer neutralReviews) { this.neutralReviews = neutralReviews; }

    public Integer getNegativeReviews() { return negativeReviews; }
    public void setNegativeReviews(Integer negativeReviews) { this.negativeReviews = negativeReviews; }

    public Double getCancellationRate() { return cancellationRate; }
    public void setCancellationRate(Double cancellationRate) { this.cancellationRate = cancellationRate; }

    public Double getDeliveryPerformance() { return deliveryPerformance; }
    public void setDeliveryPerformance(Double deliveryPerformance) { this.deliveryPerformance = deliveryPerformance; }

    @Override
    public String toString() {
        return "SellerReputationDTO{" +
                "score=" + score +
                ", totalReviews=" + totalReviews +
                ", cancellationRate=" + cancellationRate +
                ", deliveryPerformance=" + deliveryPerformance +
                '}';
    }
}