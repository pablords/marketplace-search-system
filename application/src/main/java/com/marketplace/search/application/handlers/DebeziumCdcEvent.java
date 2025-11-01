package com.marketplace.search.application.handlers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DebeziumCdcEvent {
  @JsonProperty("before")
  private ProductData before;

  @JsonProperty("after")
  private ProductData after;

  @JsonProperty("op")
  private String operation;

  @JsonProperty("ts_ms")
  private Long timestamp;

  @JsonProperty("source")
  private SourceInfo source;

  // Getters e Setters
  public ProductData getBefore() {
    return before;
  }

  public void setBefore(ProductData before) {
    this.before = before;
  }

  public ProductData getAfter() {
    return after;
  }

  public void setAfter(ProductData after) {
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

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SourceInfo {
    @JsonProperty("db")
    private String database;

    @JsonProperty("schema")
    private String schema;

    @JsonProperty("table")
    private String table;

    @JsonProperty("connector")
    private String connector;

    // Getters e Setters
    public String getDatabase() {
      return database;
    }

    public void setDatabase(String database) {
      this.database = database;
    }

    public String getSchema() {
      return schema;
    }

    public void setSchema(String schema) {
      this.schema = schema;
    }

    public String getTable() {
      return table;
    }

    public void setTable(String table) {
      this.table = table;
    }

    public String getConnector() {
      return connector;
    }

    public void setConnector(String connector) {
      this.connector = connector;
    }
  }
}
