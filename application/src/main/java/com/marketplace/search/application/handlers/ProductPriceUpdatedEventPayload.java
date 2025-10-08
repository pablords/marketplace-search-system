package com.marketplace.search.application.handlers;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Payload para evento de atualização de preço
 */
public class ProductPriceUpdatedEventPayload {
    
    @JsonProperty("product_id")
    private String productId;
    
    @JsonProperty("old_price")
    private BigDecimal oldPrice;
    
    @JsonProperty("new_price")
    private BigDecimal newPrice;
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("timestamp")
    private String timestamp;

    // Constructors
    public ProductPriceUpdatedEventPayload() {}

    // Getters and Setters
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public BigDecimal getOldPrice() { return oldPrice; }
    public void setOldPrice(BigDecimal oldPrice) { this.oldPrice = oldPrice; }

    public BigDecimal getNewPrice() { return newPrice; }
    public void setNewPrice(BigDecimal newPrice) { this.newPrice = newPrice; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}