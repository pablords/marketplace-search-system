package com.marketplace.search.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO para localização do usuário
 */
public record UserLocationDTO(
    
    @JsonProperty("country") String country,
    
    @JsonProperty("state") String state,
    
    @JsonProperty("city") String city,
    
    @JsonProperty("zip_code") String zipCode,
    
    @JsonProperty("latitude") Double latitude,
    
    @JsonProperty("longitude") Double longitude

) {
  
  public static Builder builder() {
    return new Builder();
  }
  
  public static class Builder {
    private String country;
    private String state;
    private String city;
    private String zipCode;
    private Double latitude;
    private Double longitude;
    
    public Builder country(String country) {
      this.country = country;
      return this;
    }
    
    public Builder state(String state) {
      this.state = state;
      return this;
    }
    
    public Builder city(String city) {
      this.city = city;
      return this;
    }
    
    public Builder zipCode(String zipCode) {
      this.zipCode = zipCode;
      return this;
    }
    
    public Builder latitude(Double latitude) {
      this.latitude = latitude;
      return this;
    }
    
    public Builder longitude(Double longitude) {
      this.longitude = longitude;
      return this;
    }
    
    public UserLocationDTO build() {
      return new UserLocationDTO(country, state, city, zipCode, latitude, longitude);
    }
  }
}