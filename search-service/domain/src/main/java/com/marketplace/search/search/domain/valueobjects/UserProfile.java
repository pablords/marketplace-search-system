package com.marketplace.search.search.domain.valueobjects;

import java.time.Instant;

/**
 * Value Object representando o perfil do usuário
 */
public record UserProfile(
    UserTier tier,
    Instant memberSince,
    boolean isPremium,
    int totalPurchases,
    double averageOrderValue,
    UserPreferences preferences
) {
    public UserProfile {
        if (tier == null) {
            throw new IllegalArgumentException("User tier cannot be null");
        }
        if (totalPurchases < 0) {
            throw new IllegalArgumentException("Total purchases cannot be negative");
        }
        if (averageOrderValue < 0) {
            throw new IllegalArgumentException("Average order value cannot be negative");
        }
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

