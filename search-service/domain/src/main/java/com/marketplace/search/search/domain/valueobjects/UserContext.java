package com.marketplace.search.search.domain.valueobjects;

import java.util.Set;

import com.marketplace.search.search.domain.entities.Category;

import jakarta.validation.constraints.NotNull;

/**
 * Value Object representando o contexto do usuário para personalização de busca
 */
public record UserContext(
    String userId,
    @NotNull UserLocation location,
    @NotNull Set<String> preferredCategories,
    @NotNull Set<String> purchaseHistory,
    @NotNull Set<String> searchHistory,
    @NotNull Set<String> viewHistory,
    UserProfile profile
) {
    public UserContext {
        if (location == null) {
            throw new IllegalArgumentException("User location cannot be null");
        }
        if (preferredCategories == null) {
            throw new IllegalArgumentException("Preferred categories cannot be null");
        }
        if (purchaseHistory == null) {
            throw new IllegalArgumentException("Purchase history cannot be null");
        }
        if (searchHistory == null) {
            throw new IllegalArgumentException("Search history cannot be null");
        }
        if (viewHistory == null) {
            throw new IllegalArgumentException("View history cannot be null");
        }
    }

    /**
     * Verifica se o usuário tem interesse em uma categoria específica
     */
    public boolean hasInterestInCategory(Category category) {
        if (category == null) return false;
        
        // Verifica categorias preferidas
        if (preferredCategories.contains(category.getId()) || 
            preferredCategories.contains(category.getPath())) {
            return true;
        }
        
        // Verifica histórico de busca
        return searchHistory.stream()
                .anyMatch(search -> search.toLowerCase().contains(category.getName().toLowerCase()));
    }

    /**
     * Verifica se o usuário já comprou do vendedor
     */
    public boolean hasPreviousPurchaseFromSeller(String sellerId) {
        return purchaseHistory.contains(sellerId);
    }

    /**
     * Verifica se o usuário já visualizou o produto
     */
    public boolean hasViewedProduct(String productId) {
        return viewHistory.contains(productId);
    }

    /**
     * Obtém o fator de personalização para uma categoria (0.0 a 1.0)
     */
    public double getCategoryPersonalizationFactor(Category category) {
        if (hasInterestInCategory(category)) {
            return 0.3; // Boost para categorias de interesse
        }
        return 0.0;
    }

    /**
     * Verifica se é um usuário anônimo
     */
    public boolean isAnonymous() {
        return userId == null || userId.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "UserContext{" +
                "userId='" + userId + '\'' +
                ", location=" + location +
                ", preferredCategories=" + preferredCategories.size() +
                ", purchaseHistory=" + purchaseHistory.size() +
                '}';
    }
}

