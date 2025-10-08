package com.marketplace.search.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

/**
 * DTO para contexto do usuário
 */
public class UserContextDTO {
    
    @JsonProperty("user_id")
    private String userId;
    
    @JsonProperty("location")
    private UserLocationDTO location;
    
    @JsonProperty("preferred_categories")
    private Set<String> preferredCategories;
    
    @JsonProperty("purchase_history")
    private Set<String> purchaseHistory;
    
    @JsonProperty("search_history")
    private Set<String> searchHistory;
    
    @JsonProperty("view_history")
    private Set<String> viewHistory;

    // Constructors
    public UserContextDTO() {}

    public UserContextDTO(String userId, UserLocationDTO location) {
        this.userId = userId;
        this.location = location;
    }

    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public UserLocationDTO getLocation() { return location; }
    public void setLocation(UserLocationDTO location) { this.location = location; }

    public Set<String> getPreferredCategories() { return preferredCategories; }
    public void setPreferredCategories(Set<String> preferredCategories) { this.preferredCategories = preferredCategories; }

    public Set<String> getPurchaseHistory() { return purchaseHistory; }
    public void setPurchaseHistory(Set<String> purchaseHistory) { this.purchaseHistory = purchaseHistory; }

    public Set<String> getSearchHistory() { return searchHistory; }
    public void setSearchHistory(Set<String> searchHistory) { this.searchHistory = searchHistory; }

    public Set<String> getViewHistory() { return viewHistory; }
    public void setViewHistory(Set<String> viewHistory) { this.viewHistory = viewHistory; }

    @Override
    public String toString() {
        return "UserContextDTO{" +
                "userId='" + userId + '\'' +
                ", location=" + location +
                '}';
    }
}