package com.marketplace.search.search.domain.valueobjects;

public record UserPreferences(
    boolean allowsPersonalization,
    boolean allowsRecommendations,
    boolean allowsLocationTracking) {
}

