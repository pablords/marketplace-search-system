package com.marketplace.search.application.handlers;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload para evento de atualização de estoque
 */
public class ProductStockUpdatedEventPayload {
    
    @JsonProperty("product_id")
    private String productId;
    
    @JsonProperty("old_quantity")
    private Integer oldQuantity;
    
    @JsonProperty("new_quantity")
    private Integer newQuantity;
    
    @JsonProperty("timestamp")
    private String timestamp;

    // Constructors
    public ProductStockUpdatedEventPayload() {}

    // Getters and Setters
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public Integer getOldQuantity() { return oldQuantity; }
    public void setOldQuantity(Integer oldQuantity) { this.oldQuantity = oldQuantity; }

    public Integer getNewQuantity() { return newQuantity; }
    public void setNewQuantity(Integer newQuantity) { this.newQuantity = newQuantity; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}