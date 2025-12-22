package com.marketplace.search.application.queries;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserLocationData(
    @JsonProperty("country") String country,

    @JsonProperty("state") String state,

    @JsonProperty("city") String city,

    @JsonProperty("zip_code") String zipCode,

    @JsonProperty("latitude") Double latitude,

    @JsonProperty("longitude") Double longitude) {

  public static UserLocationData of(String country, String state, String city) {
    return new UserLocationData(country, state, city, null, null, null);
  }

}
