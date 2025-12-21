package com.marketplace.search.indexing.application.handlers.payloads;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductMetricsPayload {
  @JsonProperty("product_id")
  private String productId;

  @JsonProperty("total_sales")
  private Integer totalSales;

  @JsonProperty("total_reviews")
  private Integer totalReviews;

  @JsonProperty("ctr")
  private String ctr;

  @JsonProperty("average_rating")
  private String averageRating;

  @JsonProperty("stock_quantity")
  private Integer stockQuantity;

  @JsonProperty("popularity")
  private Integer popularity;

  @JsonProperty("last_sale")
  private Long lastSale;

  @JsonProperty("last_view")
  private Long lastView;

  @JsonProperty("quality")
  private Integer quality;

  @JsonProperty("updated_at")
  private Long updatedAt;

  // Getters e Setters
  public String getProductId() {
    return productId;
  }

  public void setProductId(String productId) {
    this.productId = productId;
  }

  public Integer getTotalSales() {
    return totalSales;
  }

  public void setTotalSales(Integer totalSales) {
    this.totalSales = totalSales;
  }

  public Integer getTotalReviews() {
    return totalReviews;
  }

  public void setTotalReviews(Integer totalReviews) {
    this.totalReviews = totalReviews;
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

  public Integer getStockQuantity() {
    return stockQuantity;
  }

  public void setStockQuantity(Integer stockQuantity) {
    this.stockQuantity = stockQuantity;
  }

  public Integer getPopularity() {
    return popularity;
  }

  public void setPopularity(Integer popularity) {
    this.popularity = popularity;
  }

  public Long getLastSale() {
    return lastSale;
  }

  public void setLastSale(Long lastSale) {
    this.lastSale = lastSale;
  }

  public Long getLastView() {
    return lastView;
  }

  public void setLastView(Long lastView) {
    this.lastView = lastView;
  }

  public Integer getQuality() {
    return quality;
  }

  public void setQuality(Integer quality) {
    this.quality = quality;
  }

  public Long getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Long updatedAt) {
    this.updatedAt = updatedAt;
  }
}

