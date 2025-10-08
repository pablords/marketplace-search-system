package com.marketplace.search.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * DTO para criação/atualização de produto
 */
public class ProductDTO {
    
    @NotNull
    @NotBlank
    @JsonProperty("id")
    private String id;
    
    @NotNull
    @NotBlank
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("description")
    private String description;
    
    @NotNull
    @Positive
    @JsonProperty("price")
    private BigDecimal price;
    
    @NotNull
    @JsonProperty("currency")
    private String currency;
    
    @NotNull
    @JsonProperty("category")
    private CategoryDTO category;
    
    @NotNull
    @JsonProperty("brand")
    private BrandDTO brand;
    
    @NotNull
    @JsonProperty("seller")
    private SellerDTO seller;
    
    @JsonProperty("images")
    private List<String> images;
    
    @JsonProperty("attributes")
    private Set<String> attributes;
    
    @JsonProperty("tags")
    private Set<String> tags;
    
    @JsonProperty("stock_quantity")
    private Integer stockQuantity;
    
    @JsonProperty("condition")
    private String condition;
    
    @JsonProperty("is_active")
    private Boolean isActive;

    // Constructors
    public ProductDTO() {}

    public ProductDTO(String id, String title, String description, BigDecimal price,
                     String currency, CategoryDTO category, BrandDTO brand, SellerDTO seller) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.price = price;
        this.currency = currency;
        this.category = category;
        this.brand = brand;
        this.seller = seller;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public CategoryDTO getCategory() { return category; }
    public void setCategory(CategoryDTO category) { this.category = category; }

    public BrandDTO getBrand() { return brand; }
    public void setBrand(BrandDTO brand) { this.brand = brand; }

    public SellerDTO getSeller() { return seller; }
    public void setSeller(SellerDTO seller) { this.seller = seller; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }

    public Set<String> getAttributes() { return attributes; }
    public void setAttributes(Set<String> attributes) { this.attributes = attributes; }

    public Set<String> getTags() { return tags; }
    public void setTags(Set<String> tags) { this.tags = tags; }

    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    @Override
    public String toString() {
        return "ProductDTO{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", price=" + price +
                ", currency='" + currency + '\'' +
                '}';
    }
}