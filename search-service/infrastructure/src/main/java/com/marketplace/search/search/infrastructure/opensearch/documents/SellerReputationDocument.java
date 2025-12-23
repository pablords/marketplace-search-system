package com.marketplace.search.search.infrastructure.opensearch.documents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SellerReputationDocument {
    @JsonProperty("score")
    private double score;

    @JsonProperty("total_reviews")
    private int totalReviews;

    @JsonProperty("positive_reviews")
    private int positiveReviews;

    @JsonProperty("neutral_reviews")
    private int neutralReviews;

    @JsonProperty("negative_reviews")
    private int negativeReviews;

    @JsonProperty("cancellation_rate")
    private double cancellationRate;

    @JsonProperty("delivery_performance")
    private double deliveryPerformance;

    public SellerReputationDocument() {
    }

    public SellerReputationDocument(double score, int totalReviews, int positiveReviews, int neutralReviews, int negativeReviews, double cancellationRate, double deliveryPerformance) {
        this.score = score;
        this.totalReviews = totalReviews;
        this.positiveReviews = positiveReviews;
        this.neutralReviews = neutralReviews;
        this.negativeReviews = negativeReviews;
        this.cancellationRate = cancellationRate;
        this.deliveryPerformance = deliveryPerformance;
    }

    public double getScore() {
        return score;
    }

    public int getTotalReviews() {
        return totalReviews;
    }

    public int getPositiveReviews() {
        return positiveReviews;
    }

    public int getNeutralReviews() {
        return neutralReviews;
    }

    public int getNegativeReviews() {
        return negativeReviews;
    }

    public double getCancellationRate() {
        return cancellationRate;
    }

    public double getDeliveryPerformance() {
        return deliveryPerformance;
    }   

    public void setScore(double score) {
        this.score = score;
    }

    public void setTotalReviews(int totalReviews) {
        this.totalReviews = totalReviews;
    }
    
    public void setPositiveReviews(int positiveReviews) {
        this.positiveReviews = positiveReviews;
    }

    public void setNeutralReviews(int neutralReviews) {
        this.neutralReviews = neutralReviews;
    }
    
    public void setNegativeReviews(int negativeReviews) {
        this.negativeReviews = negativeReviews;
    }

    public void setCancellationRate(double cancellationRate) {
        this.cancellationRate = cancellationRate;
    }
    
    
    public void setDeliveryPerformance(double deliveryPerformance) {
        this.deliveryPerformance = deliveryPerformance;
    }

    public double getNormalizedScore() {
        return score;
    }

    public void setNormalizedScore(double normalizedScore) {
        this.score = normalizedScore;
    }
}
