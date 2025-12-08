package com.marketplace.search.catalog.infrastructure.persistence.entities;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "product_metrics")
@Data
public class ProductMetricsEntity {

    @Id
    @Column(name = "product_id")
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "product_id")
    private ProductEntity product;

    @Column(name = "total_sales")
    private int totalSales = 0;

    @Column(name = "total_reviews")
    private int totalReviews = 0;

    @Column(name = "ctr", precision = 5, scale = 2)
    private BigDecimal ctr = BigDecimal.ZERO;

    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "stock_quantity")
    private int stockQuantity = 0;

    @Column(name = "popularity")
    private int popularity = 0;

    @Column(name = "last_sale")
    private Instant lastSale;

    @Column(name = "last_view")
    private Instant lastView;

    @Column(name = "quality")
    private double quality = 0;



    // Construtores, Getters e Setters
}