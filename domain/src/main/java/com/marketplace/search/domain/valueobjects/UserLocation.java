package com.marketplace.search.domain.valueobjects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

/**
 * Value Object representando a localização do usuário
 */
public class UserLocation {
    
    @NotNull
    @NotBlank
    private final String country;
    
    @NotNull
    @NotBlank
    private final String state;
    
    @NotNull
    @NotBlank
    private final String city;
    
    private final String zipCode;
    
    private final Double latitude;
    
    private final Double longitude;

    public UserLocation(String country, String state, String city, String zipCode, 
                       Double latitude, Double longitude) {
        this.country = validateCountry(country);
        this.state = validateState(state);
        this.city = validateCity(city);
        this.zipCode = zipCode;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    private String validateCountry(String country) {
        if (country == null || country.trim().isEmpty()) {
            throw new IllegalArgumentException("Country cannot be null or empty");
        }
        return country.trim().toUpperCase();
    }

    private String validateState(String state) {
        if (state == null || state.trim().isEmpty()) {
            throw new IllegalArgumentException("State cannot be null or empty");
        }
        return state.trim();
    }

    private String validateCity(String city) {
        if (city == null || city.trim().isEmpty()) {
            throw new IllegalArgumentException("City cannot be null or empty");
        }
        return city.trim();
    }

    public static UserLocation of(String country, String state, String city) {
        return new UserLocation(country, state, city, null, null, null);
    }

    public static UserLocation withCoordinates(String country, String state, String city, 
                                             Double latitude, Double longitude) {
        return new UserLocation(country, state, city, null, latitude, longitude);
    }

    /**
     * Verifica se a localização tem coordenadas geográficas
     */
    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    /**
     * Calcula a distância em quilômetros para outra localização (aproximada)
     */
    public double distanceToKm(UserLocation other) {
        if (!this.hasCoordinates() || !other.hasCoordinates()) {
            throw new IllegalStateException("Both locations must have coordinates");
        }
        
        // Fórmula de Haversine simplificada
        double lat1Rad = Math.toRadians(this.latitude);
        double lat2Rad = Math.toRadians(other.latitude);
        double deltaLatRad = Math.toRadians(other.latitude - this.latitude);
        double deltaLngRad = Math.toRadians(other.longitude - this.longitude);

        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLngRad / 2) * Math.sin(deltaLngRad / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return 6371.0 * c; // Raio da Terra em km
    }

    /**
     * Verifica se está na mesma região (país e estado)
     */
    public boolean isSameRegion(UserLocation other) {
        return this.country.equals(other.country) && 
               this.state.equalsIgnoreCase(other.state);
    }

    // Getters
    public String getCountry() { return country; }
    public String getState() { return state; }
    public String getCity() { return city; }
    public String getZipCode() { return zipCode; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserLocation that = (UserLocation) o;
        return Objects.equals(country, that.country) &&
               Objects.equals(state, that.state) &&
               Objects.equals(city, that.city) &&
               Objects.equals(zipCode, that.zipCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(country, state, city, zipCode);
    }

    @Override
    public String toString() {
        return "UserLocation{" +
                "country='" + country + '\'' +
                ", state='" + state + '\'' +
                ", city='" + city + '\'' +
                (zipCode != null ? ", zipCode='" + zipCode + '\'' : "") +
                '}';
    }
}