package com.marketplace.search.indexing.domain.entities;

import java.time.Instant;
import java.util.Objects;

import com.marketplace.search.indexing.domain.valueobjects.ProductId;
import com.marketplace.search.indexing.domain.valueobjects.ProductInfo;
import com.marketplace.search.indexing.domain.valueobjects.ProductMetrics;
import com.marketplace.search.indexing.domain.valueobjects.ProductStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class Product {
    @NotNull
    private final ProductId id;

    @NotNull
    @Valid
    private final ProductInfo info;

    @NotNull
    @Valid
    private final Seller seller;

    @NotNull
    @Valid
    private final ProductMetrics metrics;

    @NotNull
    @Valid
    private final ProductStatus status;

    @NotNull
    private final Instant createdAt;

    @NotNull
    private final Instant updatedAt;

    private Product(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "Product ID cannot be null");
        this.info = Objects.requireNonNull(builder.info, "Product info cannot be null");
        this.seller = Objects.requireNonNull(builder.seller, "Seller cannot be null");
        this.metrics = Objects.requireNonNull(builder.metrics, "Metrics cannot be null");
        this.status = Objects.requireNonNull(builder.status, "Status cannot be null");
        this.createdAt = Objects.requireNonNull(builder.createdAt, "Created at cannot be null");
        this.updatedAt = Objects.requireNonNull(builder.updatedAt, "Updated at cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ProductId id;
        private ProductInfo info;
        private Seller seller;
        private ProductMetrics metrics;
        private ProductStatus status;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(ProductId id) {
            this.id = id;
            return this;
        }

        public Builder info(ProductInfo info) {
            this.info = info;
            return this;
        }

        public Builder seller(Seller seller) {
            this.seller = seller;
            return this;
        }

        public Builder metrics(ProductMetrics metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder status(ProductStatus status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Product build() {
            return new Product(this);
        }
    }

    // Getters
    public ProductId getId() {
        return id;
    }

    public ProductInfo getInfo() {
        return info;
    }

    public Seller getSeller() {
        return seller;
    }

    public ProductMetrics getMetrics() {
        return metrics;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

}
