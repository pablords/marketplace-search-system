package com.marketplace.search.domain.valueobjects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;
import java.util.Set;

/**
 * Value Object representando o contexto do usuário para personalização de busca
 */
public class UserContext {
    
    private final String userId;
    
    @NotNull
    private final UserLocation location;
    
    @NotNull
    private final Set<String> preferredCategories;
    
    @NotNull
    private final Set<String> purchaseHistory; // IDs dos vendedores
    
    @NotNull
    private final Set<String> searchHistory;
    
    @NotNull
    private final Set<String> viewHistory; // IDs dos produtos visualizados
    
    private final UserProfile profile;

    public UserContext(String userId, UserLocation location, Set<String> preferredCategories,
                      Set<String> purchaseHistory, Set<String> searchHistory, 
                      Set<String> viewHistory, UserProfile profile) {
        this.userId = userId;
        this.location = Objects.requireNonNull(location, "User location cannot be null");
        this.preferredCategories = Objects.requireNonNull(preferredCategories, "Preferred categories cannot be null");
        this.purchaseHistory = Objects.requireNonNull(purchaseHistory, "Purchase history cannot be null");
        this.searchHistory = Objects.requireNonNull(searchHistory, "Search history cannot be null");
        this.viewHistory = Objects.requireNonNull(viewHistory, "View history cannot be null");
        this.profile = profile;
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

    // Getters
    public String getUserId() { return userId; }
    public UserLocation getLocation() { return location; }
    public Set<String> getPreferredCategories() { return preferredCategories; }
    public Set<String> getPurchaseHistory() { return purchaseHistory; }
    public Set<String> getSearchHistory() { return searchHistory; }
    public Set<String> getViewHistory() { return viewHistory; }
    public UserProfile getProfile() { return profile; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserContext that = (UserContext) o;
        return Objects.equals(userId, that.userId) &&
               Objects.equals(location, that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, location);
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