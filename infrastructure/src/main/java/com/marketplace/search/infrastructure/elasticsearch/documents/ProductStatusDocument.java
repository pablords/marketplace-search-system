package com.marketplace.search.infrastructure.elasticsearch.documents;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Documento para status do produto no Elasticsearch
 */
public class ProductStatusDocument {
    
    @JsonProperty("is_active")
    private Boolean isActive;
    
    @JsonProperty("is_suspended")
    private Boolean isSuspended;
    
    @JsonProperty("has_stock")
    private Boolean hasStock;

    // Constructors
    public ProductStatusDocument() {}

    public ProductStatusDocument(Boolean isActive, Boolean isSuspended, Boolean hasStock) {
        this.isActive = isActive;
        this.isSuspended = isSuspended;
        this.hasStock = hasStock;
    }

    // Getters and Setters
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Boolean getIsSuspended() { return isSuspended; }
    public void setIsSuspended(Boolean isSuspended) { this.isSuspended = isSuspended; }

    public Boolean getHasStock() { return hasStock; }
    public void setHasStock(Boolean hasStock) { this.hasStock = hasStock; }
}