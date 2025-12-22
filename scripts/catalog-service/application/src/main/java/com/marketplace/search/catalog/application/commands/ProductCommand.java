package com.marketplace.search.catalog.application.commands;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.marketplace.search.catalog.application.payloads.BrandPaylod;
import com.marketplace.search.catalog.application.payloads.CategoryPayload;
import com.marketplace.search.catalog.application.payloads.ProductMetricsPayload;
import com.marketplace.search.catalog.application.payloads.SellerPayload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductCommand(
    @NotNull @NotBlank @JsonProperty("id") String id,

    @NotNull @NotBlank @JsonProperty("title") String title,

    @JsonProperty("description") String description,

    @NotNull @Positive @JsonProperty("price") BigDecimal price,

    @NotNull @JsonProperty("currency") String currency,

    @NotNull @JsonProperty("category") CategoryPayload category,

    @NotNull @JsonProperty("brand") BrandPaylod brand,

    @NotNull @JsonProperty("seller") SellerPayload seller,

    @JsonProperty("images") List<String> images,

    @JsonProperty("attributes") Set<String> attributes,

    @JsonProperty("tags") Set<String> tags,

    @JsonProperty("available_quantity") Integer stockQuantity,

    @JsonProperty("condition") String condition,

    @JsonProperty("is_active") Boolean isActive,
    @JsonProperty("metrics") ProductMetricsPayload productMetrics) {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private String id;
    private String title;
    private String description;
    private BigDecimal price;
    private String currency;
    private CategoryPayload category;
    private BrandPaylod brand;
    private SellerPayload seller;
    private List<String> images;
    private Set<String> attributes;
    private Set<String> tags;
    private Integer stockQuantity;
    private String condition;
    private Boolean isActive;
    private ProductMetricsPayload productMetrics;

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

    public Builder category(CategoryPayload category) {
      this.category = category;
      return this;
    }

    public Builder brand(BrandPaylod brand) {
      this.brand = brand;
      return this;
    }

    public Builder seller(SellerPayload seller) {
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

    public Builder productMetrics(ProductMetricsPayload productMetrics) {
      this.productMetrics = productMetrics;
      return this;
    }

    public ProductCommand build() {
      return new ProductCommand(
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
          productMetrics

      );
    }
  }

}
