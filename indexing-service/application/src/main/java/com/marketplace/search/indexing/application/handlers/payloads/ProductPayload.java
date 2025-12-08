package com.marketplace.search.indexing.application.handlers.payloads;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductPayload {
  @JsonProperty("id")
  private String id;

  @JsonProperty("title")
  private String title;

  @JsonProperty("description")
  private String description;

  @JsonProperty("price")
  private String price;

  @JsonProperty("currency")
  private String currency;

  @JsonProperty("available_quantity")
  private Integer availableQuantity;

  @JsonProperty("condition")
  private String condition;

  @JsonProperty("status")
  private String status;

  @JsonProperty("category_id")
  private String categoryId;

  @JsonProperty("category_name")
  private String categoryName;

  @JsonProperty("category_path")
  private String categoryPath;

  @JsonProperty("brand_id")
  private String brandId;

  @JsonProperty("brand_name")
  private String brandName;

  @JsonProperty("brand_description")
  private String brandDescription;

  @JsonProperty("seller_id")
  private String sellerId;

  @JsonProperty("seller_name")
  private String sellerName;

  @JsonProperty("seller_type")
  private String sellerType;

  @JsonProperty("seller_status")
  private String sellerStatus;

  @JsonProperty("seller_score")
  private String sellerScore;

  @JsonProperty("seller_total_reviews")
  private Integer sellerTotalReviews;

  @JsonProperty("seller_positive_reviews")
  private Integer sellerPositiveReviews;

  @JsonProperty("seller_neutral_reviews")
  private Integer sellerNeutralReviews;

  @JsonProperty("seller_negative_reviews")
  private Integer sellerNegativeReviews;

  @JsonProperty("seller_cancellation_rate")
  private String sellerCancellationRate;

  @JsonProperty("seller_delivery_performance")
  private String sellerDeliveryPerformance;

  @JsonProperty("total_sold")
  private Integer totalSold;

  @JsonProperty("view_count")
  private Integer viewCount;

  @JsonProperty("ctr")
  private String ctr;

  @JsonProperty("average_rating")
  private String averageRating;

  @JsonProperty("review_count")
  private Integer reviewCount;

  @JsonProperty("attributes")
  private String attributes;

  @JsonProperty("created_at")
  private Long createdAt;

  @JsonProperty("updated_at")
  private Long updatedAt;

  // Getters e Setters
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getPrice() {
    return price;
  }

  public void setPrice(String price) {
    this.price = price;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Integer getAvailableQuantity() {
    return availableQuantity;
  }

  public void setAvailableQuantity(Integer availableQuantity) {
    this.availableQuantity = availableQuantity;
  }

  public String getCondition() {
    return condition;
  }

  public void setCondition(String condition) {
    this.condition = condition;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(String categoryId) {
    this.categoryId = categoryId;
  }

  public String getCategoryName() {
    return categoryName;
  }

  public void setCategoryName(String categoryName) {
    this.categoryName = categoryName;
  }

  public String getCategoryPath() {
    return categoryPath;
  }

  public void setCategoryPath(String categoryPath) {
    this.categoryPath = categoryPath;
  }

  public String getBrandId() {
    return brandId;
  }

  public void setBrandId(String brandId) {
    this.brandId = brandId;
  }

  public String getBrandName() {
    return brandName;
  }

  public void setBrandName(String brandName) {
    this.brandName = brandName;
  }

  public String getBrandDescription() {
    return brandDescription;
  }

  public void setBrandDescription(String brandDescription) {
    this.brandDescription = brandDescription;
  }

  public String getSellerId() {
    return sellerId;
  }

  public void setSellerId(String sellerId) {
    this.sellerId = sellerId;
  }

  public String getSellerName() {
    return sellerName;
  }

  public void setSellerName(String sellerName) {
    this.sellerName = sellerName;
  }

  public String getSellerType() {
    return sellerType;
  }

  public void setSellerType(String sellerType) {
    this.sellerType = sellerType;
  }

  public String getSellerStatus() {
    return sellerStatus;
  }

  public void setSellerStatus(String sellerStatus) {
    this.sellerStatus = sellerStatus;
  }

  public String getSellerScore() {
    return sellerScore;
  }

  public void setSellerScore(String sellerScore) {
    this.sellerScore = sellerScore;
  }

  public Integer getSellerTotalReviews() {
    return sellerTotalReviews;
  }

  public void setSellerTotalReviews(Integer sellerTotalReviews) {
    this.sellerTotalReviews = sellerTotalReviews;
  }

  public Integer getSellerPositiveReviews() {
    return sellerPositiveReviews;
  }

  public void setSellerPositiveReviews(Integer sellerPositiveReviews) {
    this.sellerPositiveReviews = sellerPositiveReviews;
  }

  public Integer getSellerNeutralReviews() {
    return sellerNeutralReviews;
  }

  public void setSellerNeutralReviews(Integer sellerNeutralReviews) {
    this.sellerNeutralReviews = sellerNeutralReviews;
  }

  public Integer getSellerNegativeReviews() {
    return sellerNegativeReviews;
  }

  public void setSellerNegativeReviews(Integer sellerNegativeReviews) {
    this.sellerNegativeReviews = sellerNegativeReviews;
  }

  public String getSellerCancellationRate() {
    return sellerCancellationRate;
  }

  public void setSellerCancellationRate(String sellerCancellationRate) {
    this.sellerCancellationRate = sellerCancellationRate;
  }

  public String getSellerDeliveryPerformance() {
    return sellerDeliveryPerformance;
  }

  public void setSellerDeliveryPerformance(String sellerDeliveryPerformance) {
    this.sellerDeliveryPerformance = sellerDeliveryPerformance;
  }

  public Integer getTotalSold() {
    return totalSold;
  }

  public void setTotalSold(Integer totalSold) {
    this.totalSold = totalSold;
  }

  public Integer getViewCount() {
    return viewCount;
  }

  public void setViewCount(Integer viewCount) {
    this.viewCount = viewCount;
  }

  public String getCtr() {
    return ctr;
  }

  public void setCtr(String ctr) {
    this.ctr = ctr;
  }

  public String getAverageRating() {
    return averageRating;
  }

  public void setAverageRating(String averageRating) {
    this.averageRating = averageRating;
  }

  public Integer getReviewCount() {
    return reviewCount;
  }

  public void setReviewCount(Integer reviewCount) {
    this.reviewCount = reviewCount;
  }

  public String getAttributes() {
    return attributes;
  }

  public void setAttributes(String attributes) {
    this.attributes = attributes;
  }

  public Long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Long createdAt) {
    this.createdAt = createdAt;
  }

  public Long getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Long updatedAt) {
    this.updatedAt = updatedAt;
  }
}
