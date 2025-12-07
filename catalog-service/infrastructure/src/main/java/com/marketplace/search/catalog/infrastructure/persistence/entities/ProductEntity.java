package com.marketplace.search.catalog.infrastructure.persistence.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Entidade JPA que representa um produto no PostgreSQL.
 * Esta tabela é monitorada pelo Debezium para CDC.
 */
@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    @Column(name = "id", length = 255)
    private String id;

    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "currency", length = 10, nullable = false)
    private String currency = "BRL";

    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity = 0;

    @Column(name = "condition", length = 20, nullable = false)
    private String condition = "NEW";

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    // Categoria
    @Column(name = "category_id", length = 255)
    private String categoryId;

    @Column(name = "category_name", length = 255)
    private String categoryName;

    @Column(name = "category_path", length = 500)
    private String categoryPath;

    // Marca
    @Column(name = "brand_id", length = 255)
    private String brandId;

    @Column(name = "brand_name", length = 255)
    private String brandName;

    @Column(name = "brand_description", columnDefinition = "TEXT")
    private String brandDescription;

    // Vendedor
    @Column(name = "seller_id", length = 255)
    private String sellerId;

    @Column(name = "seller_name", length = 255)
    private String sellerName;

    @Column(name = "seller_type", length = 50)
    private String sellerType;

    @Column(name = "seller_status", length = 50)
    private String sellerStatus;

    @Column(name = "seller_score", precision = 5, scale = 2)
    private BigDecimal sellerScore;

    @Column(name = "seller_positive_reviews")
    private Integer sellerPositiveReviews;
    @Column(name = "seller_negative_reviews")
    private Integer sellerNegativeReviews;
    @Column(name = "seller_neutral_reviews")
    private Integer sellerNeutralReviews;
    @Column(name = "seller_total_reviews")
    private Integer sellerTotalReviews;

    @Column(name = "seller_cancellation_rate", precision = 5, scale = 2)
    private BigDecimal sellerCancellationRate;

    @Column(name = "seller_delivery_performance", precision = 5, scale = 2)
    private BigDecimal sellerDeliveryPerformance;

    // Métricas
    @Column(name = "total_sold")
    private Integer totalSold = 0;

    @Column(name = "view_count")
    private Integer viewCount = 0;

    @Column(name = "conversion_rate", precision = 5, scale = 2)
    private BigDecimal conversionRate = BigDecimal.ZERO;

    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "review_count")
    private Integer reviewCount = 0;

    // Atributos como JSONB
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", columnDefinition = "jsonb")
    private Map<String, Object> attributes;

    // Timestamps
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
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

    public BigDecimal getSellerScore() {
        return sellerScore;
    }

    public void setSellerScore(BigDecimal sellerScore) {
        this.sellerScore = sellerScore;
    }

    public Integer getSellerTotalReviews() {
        return sellerTotalReviews;
    }

    public void setSellerPositiveReviews(Integer positiveReviews) {
        this.sellerPositiveReviews = positiveReviews;
    }

    public Integer getSellerPositiveReviews() {
        return sellerPositiveReviews;
    }

    public void setSellerNegativeReviews(Integer negativeReviews) {
        this.sellerNegativeReviews = negativeReviews;
    }

    public Integer getSellerNegativeReviews() {
        return sellerNegativeReviews;
    }

    public void setSellerNeutralReviews(Integer neutralReviews) {
        this.sellerNeutralReviews = neutralReviews;
    }

    public Integer getSellerNeutralReviews() {
        return sellerNeutralReviews;
    }

    public void setSellerTotalReviews(Integer sellerTotalReviews) {
        this.sellerTotalReviews = sellerTotalReviews;
    }

    public BigDecimal getSellerCancellationRate() {
        return sellerCancellationRate;
    }

    public void setSellerCancellationRate(BigDecimal sellerCancellationRate) {
        this.sellerCancellationRate = sellerCancellationRate;
    }

    public BigDecimal getSellerDeliveryPerformance() {
        return sellerDeliveryPerformance;
    }

    public void setSellerDeliveryPerformance(BigDecimal sellerDeliveryPerformance) {
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

    public BigDecimal getConversionRate() {
        return conversionRate;
    }

    public void setConversionRate(BigDecimal conversionRate) {
        this.conversionRate = conversionRate;
    }

    public BigDecimal getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(BigDecimal averageRating) {
        this.averageRating = averageRating;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
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
}
