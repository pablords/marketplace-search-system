package com.marketplace.search.catalog.interfaces.rest.dtos;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * DTO para criação/atualização de produto
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDTO(

    @NotNull @NotBlank @JsonProperty("id") String id,

    @NotNull @NotBlank @JsonProperty("title") String title,

    @JsonProperty("description") String description,

    @NotNull @Positive @JsonProperty("price") BigDecimal price,

    @NotNull @JsonProperty("currency") String currency,

    @NotNull @JsonProperty("category") CategoryDTO category,

    @NotNull @JsonProperty("brand") BrandDTO brand,

    @NotNull @JsonProperty("seller") SellerDTO seller,

    @JsonProperty("images") List<String> images,

    @JsonProperty("attributes") Set<String> attributes,

    @JsonProperty("tags") Set<String> tags,

    @JsonProperty("available_quantity") Integer stockQuantity,

    @JsonProperty("condition") String condition,

    @JsonProperty("is_active") Boolean isActive,
    @JsonProperty("metrics") ProductMetricsDTO productMetrics

) {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private String id;
    private String title;
    private String description;
    private BigDecimal price;
    private String currency;
    private CategoryDTO category;
    private BrandDTO brand;
    private SellerDTO seller;
    private List<String> images;
    private Set<String> attributes;
    private Set<String> tags;
    private Integer stockQuantity;
    private String condition;
    private Boolean isActive;
    private ProductMetricsDTO productMetrics;

    public Builder id(String id) {
      this.id = id;
      return this;
    }

    public Builder title(String title) {
      this.title = title;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder price(BigDecimal price) {
      this.price = price;
      return this;
    }

    public Builder currency(String currency) {
      this.currency = currency;
      return this;
    }

    public Builder category(CategoryDTO category) {
      this.category = category;
      return this;
    }

    public Builder brand(BrandDTO brand) {
      this.brand = brand;
      return this;
    }

    public Builder seller(SellerDTO seller) {
      this.seller = seller;
      return this;
    }

    public Builder images(List<String> images) {
      this.images = images;
      return this;
    }

    public Builder attributes(Set<String> attributes) {
      this.attributes = attributes;
      return this;
    }

    public Builder tags(Set<String> tags) {
      this.tags = tags;
      return this;
    }

    public Builder stockQuantity(Integer stockQuantity) {
      this.stockQuantity = stockQuantity;
      return this;
    }

    public Builder condition(String condition) {
      this.condition = condition;
      return this;
    }

    public Builder isActive(Boolean isActive) {
      this.isActive = isActive;
      return this;
    }

    public Builder productMetrics(ProductMetricsDTO productMetrics) {
      this.productMetrics = productMetrics;
      return this;
    }

    public ProductDTO build() {
      return new ProductDTO(
          id,
          title,
          description,
          price,
          currency,
          category,
          brand,
          seller,
          images,
          attributes,
          tags,
          stockQuantity,
          condition,
          isActive,
          productMetrics);
    }
  }
}
