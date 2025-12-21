package com.marketplace.search.indexing.application.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.marketplace.search.indexing.application.handlers.payloads.BrandPayload;
import com.marketplace.search.indexing.application.handlers.payloads.CategoryPayload;
import com.marketplace.search.indexing.application.handlers.payloads.ProductMetricsPayload;
import com.marketplace.search.indexing.application.handlers.payloads.ProductPayload;
import com.marketplace.search.indexing.application.handlers.payloads.SellerPayload;
import com.marketplace.search.indexing.application.handlers.valueobjects.SourceInfo;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DebeziumCDCEvent {
  @JsonProperty("before")
  private Object before;

  @JsonProperty("after")
  private Object after;

  @JsonProperty("op")
  private String operation;

  @JsonProperty("ts_ms")
  private Long timestamp;

  @JsonProperty("source")
  private SourceInfo source;

  // Getters e Setters
  public Object getBefore() {
    return before;
  }

  public void setBefore(Object before) {
    this.before = before;
  }

  public Object getAfter() {
    return after;
  }

  public void setAfter(Object after) {
    this.after = after;
  }

  public String getOperation() {
    return operation;
  }

  public void setOperation(String operation) {
    this.operation = operation;
  }

  public Long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Long timestamp) {
    this.timestamp = timestamp;
  }

  public SourceInfo getSource() {
    return source;
  }

  public void setSource(SourceInfo source) {
    this.source = source;
  }

  // Helper methods para obter payloads tipados
  public ProductPayload getProductAfter() {
    if (after == null) return null;
    if (after instanceof ProductPayload) {
      return (ProductPayload) after;
    }
    return null;
  }

  public ProductPayload getProductBefore() {
    if (before == null) return null;
    if (before instanceof ProductPayload) {
      return (ProductPayload) before;
    }
    return null;
  }

  public BrandPayload getBrandAfter() {
    if (after == null) return null;
    if (after instanceof BrandPayload) {
      return (BrandPayload) after;
    }
    return null;
  }

  public CategoryPayload getCategoryAfter() {
    if (after == null) return null;
    if (after instanceof CategoryPayload) {
      return (CategoryPayload) after;
    }
    return null;
  }

  public SellerPayload getSellerAfter() {
    if (after == null) return null;
    if (after instanceof SellerPayload) {
      return (SellerPayload) after;
    }
    return null;
  }

  public ProductMetricsPayload getProductMetricsAfter() {
    if (after == null) return null;
    if (after instanceof ProductMetricsPayload) {
      return (ProductMetricsPayload) after;
    }
    return null;
  }
}
