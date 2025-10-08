package com.marketplace.search.application.handlers;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload para evento de produto removido
 */
public class ProductDeletedEventPayload {
    
    @JsonProperty("product_id")
    private String productId;
    
    @JsonProperty("timestamp")
    private String timestamp;

    // Constructors
    public ProductDeletedEventPayload() {}

    // Getters and Setters
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}