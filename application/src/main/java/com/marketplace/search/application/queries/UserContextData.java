package com.marketplace.search.application.queries;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserContextData(
    @JsonProperty("user_id") String userId,

    @JsonProperty("location") UserLocationData location,

    @JsonProperty("preferred_categories") Set<String> preferredCategories,

    @JsonProperty("purchase_history") Set<String> purchaseHistory,

    @JsonProperty("search_history") Set<String> searchHistory,

    @JsonProperty("view_history") Set<String> viewHistory) {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String userId;
    private UserLocationData location;
    private Set<String> preferredCategories;
    private Set<String> purchaseHistory;
    private Set<String> searchHistory;
    private Set<String> viewHistory;

    public Builder userId(String userId) {
      this.userId = userId;
      return this;
    }

    public Builder location(UserLocationData location) {
      this.location = location;
      return this;
    }

    public Builder preferredCategories(Set<String> preferredCategories) {
      this.preferredCategories = preferredCategories;
      return this;
    }

    public Builder purchaseHistory(Set<String> purchaseHistory) {
      this.purchaseHistory = purchaseHistory;
      return this;
    }

    public Builder searchHistory(Set<String> searchHistory) {
      this.searchHistory = searchHistory;
      return this;
    }

    public Builder viewHistory(Set<String> viewHistory) {
      this.viewHistory = viewHistory;
      return this;
    }

    public UserContextData build() {
      return new UserContextData(userId, location, preferredCategories,
          purchaseHistory, searchHistory, viewHistory);
    }
  }

}
