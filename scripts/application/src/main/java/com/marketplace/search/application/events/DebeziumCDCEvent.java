package com.marketplace.search.application.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.marketplace.search.application.handlers.payloads.ProductPayload;
import com.marketplace.search.application.handlers.valueobjects.SourceInfo;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DebeziumCDCEvent {
  @JsonProperty("before")
  private ProductPayload before;

  @JsonProperty("after")
  private ProductPayload after;

  @JsonProperty("op")
  private String operation;

  @JsonProperty("ts_ms")
  private Long timestamp;

  @JsonProperty("source")
  private SourceInfo source;

  // Getters e Setters
  public ProductPayload getBefore() {
    return before;
  }

  public void setBefore(ProductPayload before) {
    this.before = before;
  }

  public ProductPayload getAfter() {
    return after;
  }

  public void setAfter(ProductPayload after) {
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

}
