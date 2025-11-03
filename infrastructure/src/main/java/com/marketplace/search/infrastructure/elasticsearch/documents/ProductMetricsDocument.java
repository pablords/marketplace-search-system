package com.marketplace.search.infrastructure.elasticsearch.documents;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Documento para métricas do produto no Elasticsearch
 */
public class ProductMetricsDocument {

  @JsonProperty("total_sales")
  private Long totalSales;

  @JsonProperty("total_views")
  private Long totalViews;

  @JsonProperty("total_reviews")
  private Long totalReviews;

  @JsonProperty("average_rating")
  private Double averageRating;

  @JsonProperty("available_quantity")
  private int stockQuantity;

  @JsonProperty("conversion_rate")
  private double conversionRate;

  // Constructors
  public ProductMetricsDocument() {
  }

  public ProductMetricsDocument(Long totalSales, Long totalViews, Long totalReviews, Double averageRating) {
    this.totalSales = totalSales;
    this.totalViews = totalViews;
    this.totalReviews = totalReviews;
    this.averageRating = averageRating;
  }

  public ProductMetricsDocument(Long totalSales, Long totalViews, Long totalReviews,
      Double averageRating, int stockQuantity, double conversionRate) {
    this.totalSales = totalSales;
    this.totalViews = totalViews;
    this.totalReviews = totalReviews;
    this.averageRating = averageRating;
    this.stockQuantity = stockQuantity;
    this.conversionRate = conversionRate;
  }

  // Getters and Setters
  public Long getTotalSales() {
    return totalSales;
  }

  public void setTotalSales(Long totalSales) {
    this.totalSales = totalSales;
  }

  public Long getTotalViews() {
    return totalViews;
  }

  public void setTotalViews(Long totalViews) {
    this.totalViews = totalViews;
  }

  public Long getTotalReviews() {
    return totalReviews;
  }

  public void setTotalReviews(Long totalReviews) {
    this.totalReviews = totalReviews;
  }

  public Double getAverageRating() {
    return averageRating;
  }

  public void setAverageRating(Double averageRating) {
    this.averageRating = averageRating;
  }

  public int getStockQuantity() {
    return stockQuantity;
  }

  public void setStockQuantity(int stockQuantity) {
    this.stockQuantity = stockQuantity;
  }

  public double getConversionRate() {
    return conversionRate;
  }

  public void setConversionRate(double conversionRate) {
    this.conversionRate = conversionRate;
  }
}