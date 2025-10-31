package com.marketplace.search.infrastructure.elasticsearch.documents;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Documento do Elasticsearch representando um produto
 */
public class ProductDocument {

  @JsonProperty("id")
  private String id;

  @JsonProperty("title")
  private String title;

  @JsonProperty("description")
  private String description;

  @JsonProperty("price")
  private BigDecimal price;

  @JsonProperty("currency")
  private String currency;

  @JsonProperty("category")
  private CategoryDocument category;

  @JsonProperty("brand")
  private BrandDocument brand;

  @JsonProperty("seller")
  private SellerDocument seller;

  @JsonProperty("images")
  private List<String> images;

  @JsonProperty("attributes")
  private Set<String> attributes;

  @JsonProperty("tags")
  private Set<String> tags;

  @JsonProperty("metrics")
  private ProductMetricsDocument metrics;

  @JsonProperty("status")
  private ProductStatusDocument status;

  @JsonProperty("created_at")
  private Instant createdAt;

  @JsonProperty("updated_at")
  private Instant updatedAt;

  // Para otimização de busca por texto
  @JsonProperty("searchable_text")
  private String searchableText;

  // Para facilitar agregações
  @JsonProperty("price_range")
  private String priceRange;

  @JsonProperty("popularity_score")
  private Double popularityScore;

  // Constructors
  public ProductDocument() {
  }

  // Getters and Setters
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

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public CategoryDocument getCategory() {
    return category;
  }

  public void setCategory(CategoryDocument category) {
    this.category = category;
  }

  public BrandDocument getBrand() {
    return brand;
  }

  public void setBrand(BrandDocument brand) {
    this.brand = brand;
  }

  public SellerDocument getSeller() {
    return seller;
  }

  public void setSeller(SellerDocument seller) {
    this.seller = seller;
  }

  public List<String> getImages() {
    return images;
  }

  public void setImages(List<String> images) {
    this.images = images;
  }

  public Set<String> getAttributes() {
    return attributes;
  }

  public void setAttributes(Set<String> attributes) {
    this.attributes = attributes;
  }

  public Set<String> getTags() {
    return tags;
  }

  public void setTags(Set<String> tags) {
    this.tags = tags;
  }

  public ProductMetricsDocument getMetrics() {
    return metrics;
  }

  public void setMetrics(ProductMetricsDocument metrics) {
    this.metrics = metrics;
  }

  public ProductStatusDocument getStatus() {
    return status;
  }

  public void setStatus(ProductStatusDocument status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getSearchableText() {
    return searchableText;
  }

  public void setSearchableText(String searchableText) {
    this.searchableText = searchableText;
  }

  public String getPriceRange() {
    return priceRange;
  }

  public void setPriceRange(String priceRange) {
    this.priceRange = priceRange;
  }

  public Double getPopularityScore() {
    return popularityScore;
  }

  public void setPopularityScore(Double popularityScore) {
    this.popularityScore = popularityScore;
  }
}