package com.marketplace.search.application.queries;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SellerData(
    @NotNull @NotBlank @JsonProperty("id") String id,

    @NotNull @NotBlank @JsonProperty("name") String name,

    @JsonProperty("type") String type,

    @JsonProperty("reputation") SellerReputationData reputation,

    @JsonProperty("status") String status,

    @JsonProperty("member_since") String memberSince) {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String id;
    private String name;
    private String type;
    private SellerReputationData reputation;
    private String status;
    private String memberSince;

    public Builder id(String id) {
      this.id = id;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder type(String type) {
      this.type = type;
      return this;
    }

    public Builder reputation(SellerReputationData reputation) {
      this.reputation = reputation;
      return this;
    }

    public Builder status(String status) {
      this.status = status;
      return this;
    }

    public Builder memberSince(String memberSince) {
      this.memberSince = memberSince;
      return this;
    }

    public SellerData build() {
      return new SellerData(id, name, type, reputation, status, memberSince);
    }
  }

}
