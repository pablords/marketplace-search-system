package com.marketplace.search.domain.valueobjects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Value Object representando a localização do usuário
 */
public record UserLocation(
    @NotNull @NotBlank String country,
    @NotNull @NotBlank String state,
    @NotNull @NotBlank String city,
    String zipCode,
    Double latitude,
    Double longitude
) {
    public UserLocation {
        if (country == null || country.trim().isEmpty()) {
            throw new IllegalArgumentException("Country cannot be null or empty");
        }
        if (state == null || state.trim().isEmpty()) {
            throw new IllegalArgumentException("State cannot be null or empty");
        }
        if (city == null || city.trim().isEmpty()) {
            throw new IllegalArgumentException("City cannot be null or empty");
        }
    }

    public static UserLocation of(String country, String state, String city) {
        return new UserLocation(country.trim().toUpperCase(), state.trim(), city.trim(), null, null, null);
    }

    public static UserLocation withCoordinates(String country, String state, String city, 
                                             Double latitude, Double longitude) {
        return new UserLocation(country.trim().toUpperCase(), state.trim(), city.trim(), null, latitude, longitude);
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