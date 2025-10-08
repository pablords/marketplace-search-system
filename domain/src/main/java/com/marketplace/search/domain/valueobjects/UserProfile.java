package com.marketplace.search.domain.valueobjects;

import java.time.Instant;
import java.util.Objects;

/**
 * Value Object representando o perfil do usuário
 */
public class UserProfile {
    
    private final UserTier tier;
    
    private final Instant memberSince;
    
    private final boolean isPremium;
    
    private final int totalPurchases;
    
    private final double averageOrderValue;
    
    private final UserPreferences preferences;

    public UserProfile(UserTier tier, Instant memberSince, boolean isPremium,
                      int totalPurchases, double averageOrderValue, UserPreferences preferences) {
        this.tier = Objects.requireNonNull(tier, "User tier cannot be null");
        this.memberSince = memberSince;
        this.isPremium = isPremium;
        this.totalPurchases = validateTotalPurchases(totalPurchases);
        this.averageOrderValue = validateAverageOrderValue(averageOrderValue);
        this.preferences = preferences;
    }

    private int validateTotalPurchases(int totalPurchases) {
        if (totalPurchases < 0) {
            throw new IllegalArgumentException("Total purchases cannot be negative");
        }
        return totalPurchases;
    }

    private double validateAverageOrderValue(double averageOrderValue) {
        if (averageOrderValue < 0) {
            throw new IllegalArgumentException("Average order value cannot be negative");
        }
        return averageOrderValue;
    }

    /**
     * Calcula o score de fidelidade do usuário (0.0 a 1.0)
     */
    public double getLoyaltyScore() {
        double baseScore = 0.0;
        
        // Pontuação baseada no tier
        switch (tier) {
            case BRONZE -> baseScore = 0.2;
            case SILVER -> baseScore = 0.4;
            case GOLD -> baseScore = 0.6;
            case PLATINUM -> baseScore = 0.8;
            case DIAMOND -> baseScore = 1.0;
        }
        
        // Boost para usuários premium
        if (isPremium) {
            baseScore += 0.1;
        }
        
        // Ajuste baseado no histórico de compras
        if (totalPurchases > 100) {
            baseScore += 0.1;
        } else if (totalPurchases > 50) {
            baseScore += 0.05;
        }
        
        return Math.min(1.0, baseScore);
    }

    /**
     * Verifica se é um usuário de alto valor
     */
    public boolean isHighValueCustomer() {
        return (tier == UserTier.PLATINUM || tier == UserTier.DIAMOND) &&
               totalPurchases > 50 &&
               averageOrderValue > 500.0;
    }

    /**
     * Verifica se é um usuário novo
     */
    public boolean isNewCustomer() {
        if (memberSince == null) return true;
        
        long daysSinceMember = java.time.Duration.between(memberSince, Instant.now()).toDays();
        return daysSinceMember <= 30;
    }

    // Getters
    public UserTier getTier() { return tier; }
    public Instant getMemberSince() { return memberSince; }
    public boolean isPremium() { return isPremium; }
    public int getTotalPurchases() { return totalPurchases; }
    public double getAverageOrderValue() { return averageOrderValue; }
    public UserPreferences getPreferences() { return preferences; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserProfile that = (UserProfile) o;
        return isPremium == that.isPremium &&
               totalPurchases == that.totalPurchases &&
               Double.compare(that.averageOrderValue, averageOrderValue) == 0 &&
               tier == that.tier &&
               Objects.equals(memberSince, that.memberSince);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tier, memberSince, isPremium, totalPurchases, averageOrderValue);
    }

    @Override
    public String toString() {
        return "UserProfile{" +
                "tier=" + tier +
                ", isPremium=" + isPremium +
                ", totalPurchases=" + totalPurchases +
                ", averageOrderValue=" + averageOrderValue +
                '}';
    }
}

enum UserTier {
    BRONZE,
    SILVER,
    GOLD,
    PLATINUM,
    DIAMOND
}

class UserPreferences {
    private final boolean allowsPersonalization;
    private final boolean allowsRecommendations;
    private final boolean allowsLocationTracking;
    
    public UserPreferences(boolean allowsPersonalization, boolean allowsRecommendations, 
                          boolean allowsLocationTracking) {
        this.allowsPersonalization = allowsPersonalization;
        this.allowsRecommendations = allowsRecommendations;
        this.allowsLocationTracking = allowsLocationTracking;
    }
    
    // Getters
    public boolean allowsPersonalization() { return allowsPersonalization; }
    public boolean allowsRecommendations() { return allowsRecommendations; }
    public boolean allowsLocationTracking() { return allowsLocationTracking; }
}