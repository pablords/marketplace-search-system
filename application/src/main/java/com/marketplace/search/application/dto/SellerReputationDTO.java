package com.marketplace.search.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO para reputação do vendedor
 */
public record SellerReputationDTO(
    
    @JsonProperty("score") Double score,
    
    @JsonProperty("total_reviews") Integer totalReviews,
    
    @JsonProperty("positive_reviews") Integer positiveReviews,
    
    @JsonProperty("neutral_reviews") Integer neutralReviews,
    
    @JsonProperty("negative_reviews") Integer negativeReviews,
    
    @JsonProperty("cancellation_rate") Double cancellationRate,
    
    @JsonProperty("delivery_performance") Double deliveryPerformance

) {
  
  public static Builder builder() {
    return new Builder();
  }
  
  public static class Builder {
    private Double score;
    private Integer totalReviews;
    private Integer positiveReviews;
    private Integer neutralReviews;
    private Integer negativeReviews;
    private Double cancellationRate;
    private Double deliveryPerformance;
    
    public Builder score(Double score) {
      this.score = score;
      return this;
    }
    
    public Builder totalReviews(Integer totalReviews) {
      this.totalReviews = totalReviews;
      return this;
    }
    
    public Builder positiveReviews(Integer positiveReviews) {
      this.positiveReviews = positiveReviews;
      return this;
    }
    
    public Builder neutralReviews(Integer neutralReviews) {
      this.neutralReviews = neutralReviews;
      return this;
    }
    
    public Builder negativeReviews(Integer negativeReviews) {
      this.negativeReviews = negativeReviews;
      return this;
    }
    
    public Builder cancellationRate(Double cancellationRate) {
      this.cancellationRate = cancellationRate;
      return this;
    }
    
    public Builder deliveryPerformance(Double deliveryPerformance) {
      this.deliveryPerformance = deliveryPerformance;
      return this;
    }
    
    public SellerReputationDTO build() {
      return new SellerReputationDTO(score, totalReviews, positiveReviews, 
                                     neutralReviews, negativeReviews, 
                                     cancellationRate, deliveryPerformance);
    }
  }
}