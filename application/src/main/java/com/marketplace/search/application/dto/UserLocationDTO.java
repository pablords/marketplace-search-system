package com.marketplace.search.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO para localização do usuário
 */
public class UserLocationDTO {
    
    @JsonProperty("country")
    private String country;
    
    @JsonProperty("state")
    private String state;
    
    @JsonProperty("city")
    private String city;
    
    @JsonProperty("zip_code")
    private String zipCode;
    
    @JsonProperty("latitude")
    private Double latitude;
    
    @JsonProperty("longitude")
    private Double longitude;

    // Constructors
    public UserLocationDTO() {}

    public UserLocationDTO(String country, String state, String city) {
        this.country = country;
        this.state = state;
        this.city = city;
    }

    // Getters and Setters
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    @Override
    public String toString() {
        return "UserLocationDTO{" +
                "country='" + country + '\'' +
                ", state='" + state + '\'' +
                ", city='" + city + '\'' +
                '}';
    }
}