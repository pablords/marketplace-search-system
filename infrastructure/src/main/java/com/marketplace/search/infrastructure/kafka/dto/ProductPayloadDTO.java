package com.marketplace.search.infrastructure.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * DTO que representa os dados do produto vindos do PostgreSQL via Debezium.
 * Corresponde à estrutura da tabela 'products' no banco de dados.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductPayloadDTO {
  @JsonProperty("id")
  private String id;

  @JsonProperty("title")
  private String title;

  @JsonProperty("description")
  private String description;

  @JsonProperty("price")
  private Double price;

  @JsonProperty("currency")
  private String currency;

  @JsonProperty("category_id")
  private String categoryId;

  @JsonProperty("category_name")
  private String categoryName;

  @JsonProperty("category_path")
  private String categoryPath;

  @JsonProperty("category_parent_id")
  private String categoryParentId;

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

  @JsonProperty("seller_reputation_score")
  private String sellerScore;

  @JsonProperty("seller_reputation_total_reviews")
  private Integer sellerTotalReviews;

  @JsonProperty("seller_reputation_positive_reviews")
  private Integer sellerReputationPositiveReviews;

  @JsonProperty("seller_reputation_neutral_reviews")
  private Integer sellerReputationNeutralReviews;

  @JsonProperty("seller_reputation_negative_reviews")
  private Integer sellerReputationNegativeReviews;

  @JsonProperty("seller_reputation_cancellation_rate")
  private String sellerCancellationRate;

  @JsonProperty("seller_reputation_delivery_performance")
  private String sellerDeliveryPerformance;

  @JsonProperty("seller_member_since")
  private String sellerMemberSince;

  @JsonProperty("available_quantity")
  private Integer availableQuantity;

  @JsonProperty("condition")
  private String condition;

  @JsonProperty("is_active")
  private Boolean isActive;

  @JsonProperty("attributes")
  private JsonNode attributes;

  @JsonProperty("images")
  private JsonNode images;

  @JsonProperty("tags")
  private JsonNode tags;

  @JsonProperty("created_at")
  private String createdAt;

  @JsonProperty("updated_at")
  private String updatedAt;

  // Getters e setters corretos para os campos snake_case e tipos
  public String getId() {
    return id;
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

  public String getCategoryParentId() {
    return categoryParentId;
  }

  public void setCategoryParentId(String categoryParentId) {
    this.categoryParentId = categoryParentId;
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

  public Integer getSellerReputationPositiveReviews() {
    return sellerReputationPositiveReviews;
  }

  public void setSellerReputationPositiveReviews(Integer sellerReputationPositiveReviews) {
    this.sellerReputationPositiveReviews = sellerReputationPositiveReviews;
  }

  public Integer getSellerReputationNeutralReviews() {
    return sellerReputationNeutralReviews;
  }

  public void setSellerReputationNeutralReviews(Integer sellerReputationNeutralReviews) {
    this.sellerReputationNeutralReviews = sellerReputationNeutralReviews;
  }

  public Integer getSellerReputationNegativeReviews() {
    return sellerReputationNegativeReviews;
  }

  public void setSellerReputationNegativeReviews(Integer sellerReputationNegativeReviews) {
    this.sellerReputationNegativeReviews = sellerReputationNegativeReviews;
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

  public String getSellerMemberSince() {
    return sellerMemberSince;
  }

  public void setSellerMemberSince(String sellerMemberSince) {
    this.sellerMemberSince = sellerMemberSince;
  }

  public Integer getStockQuantity() {
    return availableQuantity;
  }

  public void setStockQuantity(Integer availableQuantity) {
    this.availableQuantity = availableQuantity;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean isActive) {
    this.isActive = isActive;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  public void setUpdatedAt(String updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public String getDescription() {
    return this.description;
  }

  public String getTitle() {
    return this.title;
  }

  /**
   * @return the price
   */
  public Double getPrice() {
    return price;
  }

  /**
   * @return the currency
   */
  public String getCurrency() {
    return currency;
  }

  /**
   * @return the condition
   */
  public String getCondition() {
    return condition;
  }

  /**
   * @return the attributes
   */
  public JsonNode getAttributes() {
    return attributes;
  }

  /**
   * @return the images
   */
  public JsonNode getImages() {
    return images;
  }

  /**
   * @return the tags
   */
  public JsonNode getTags() {
    return tags;
  }
}
