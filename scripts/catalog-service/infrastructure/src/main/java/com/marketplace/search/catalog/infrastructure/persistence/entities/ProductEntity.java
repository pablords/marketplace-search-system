package com.marketplace.search.catalog.infrastructure.persistence.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "products")
@Data
public class ProductEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "currency", length = 3)
    private String currency = "BRL";

    @Column(name = "available_quantity")
    private Integer availableQuantity = 0;

    @Column(name = "condition")
    private String condition = "NEW";

    @Column(name = "active")
    private boolean active = true;

    // --- RELACIONAMENTOS ---

    // FetchType.LAZY é mandatório para performance
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private CategoryEntity category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private BrandEntity brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private SellerEntity seller;

    // Cascade ALL garante que ao salvar o produto, salvamos as métricas iniciais
    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = false)
    private ProductMetricsEntity metrics;

    // --- CAMPOS ESPECÍFICOS ---

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", columnDefinition = "jsonb")
    private Map<String, Object> attributes;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (metrics == null) {
            metrics = new ProductMetricsEntity();
            metrics.setProduct(this);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters e Setters...

    // Helper para manter consistência bidirecional
    public void setMetrics(ProductMetricsEntity metrics) {
        this.metrics = metrics;
        if (metrics != null) {
            metrics.setProduct(this);
        }
    }
}