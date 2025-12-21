package com.marketplace.search.indexing.application.handlers.payloads;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SellerPayload {
  @JsonProperty("id")
  private String id;

  @JsonProperty("name")
  private String name;

  @JsonProperty("type")
  private String type;

  @JsonProperty("status")
  private String status;

  @JsonProperty("score")
  private String score;

  @JsonProperty("total_reviews")
  private Integer totalReviews;

  @JsonProperty("positive_reviews")
  private Integer positiveReviews;

  @JsonProperty("negative_reviews")
  private Integer negativeReviews;

  @JsonProperty("neutral_reviews")
  private Integer neutralReviews;

  @JsonProperty("cancellation_rate")
  private String cancellationRate;

  @JsonProperty("delivery_performance")
  private String deliveryPerformance;

  @JsonProperty("updated_at")
  private Long updatedAt;

  // Getters e Setters
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getScore() {
    return score;
  }

  public void setScore(String score) {
    this.score = score;
  }

  public Integer getTotalReviews() {
    return totalReviews;
  }

  public void setTotalReviews(Integer totalReviews) {
    this.totalReviews = totalReviews;
  }

  public Integer getPositiveReviews() {
    return positiveReviews;
  }

  public void setPositiveReviews(Integer positiveReviews) {
    this.positiveReviews = positiveReviews;
  }

  public Integer getNegativeReviews() {
    return negativeReviews;
  }

  public void setNegativeReviews(Integer negativeReviews) {
    this.negativeReviews = negativeReviews;
  }

  public Integer getNeutralReviews() {
    return neutralReviews;
  }

  public void setNeutralReviews(Integer neutralReviews) {
    this.neutralReviews = neutralReviews;
  }

  public String getCancellationRate() {
    return cancellationRate;
  }

  public void setCancellationRate(String cancellationRate) {
    this.cancellationRate = cancellationRate;
  }

  public String getDeliveryPerformance() {
    return deliveryPerformance;
  }

  public void setDeliveryPerformance(String deliveryPerformance) {
    this.deliveryPerformance = deliveryPerformance;
  }

  public Long getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Long updatedAt) {
    this.updatedAt = updatedAt;
  }
}

