package com.marketplace.search.infrastructure.elasticsearch.documents;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Documento para vendedor no Elasticsearch
 */
public class SellerDocument {

  @JsonProperty("id")
  private String id;

  @JsonProperty("name")
  private String name;

  @JsonProperty("status")
  private String status;

  @JsonProperty("type")
  private String type;

  @JsonProperty("reputation_score")
  private double reputationScore;

  // Constructors
  public SellerDocument() {
  }

  public SellerDocument(String id, String name, String status, String type) {
    this.id = id;
    this.name = name;
    this.status = status;
    this.type = type;
  }

  public SellerDocument(String id, String name, String status, String type, double reputationScore) {
    this.id = id;
    this.name = name;
    this.status = status;
    this.type = type;
    this.reputationScore = reputationScore;
  }

  // Getters and Setters
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public double getReputationScore() {
    return reputationScore;
  }

  public void setReputationScore(double reputationScore) {
    this.reputationScore = reputationScore;
  }
}