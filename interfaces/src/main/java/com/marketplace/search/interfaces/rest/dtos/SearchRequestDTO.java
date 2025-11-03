package com.marketplace.search.interfaces.rest.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTO para requisição de busca
 */
public record SearchRequestDTO(
    
    @NotNull @NotBlank @JsonProperty("query") String query,
    
    @JsonProperty("category_id") String categoryId,
    
    @JsonProperty("filters") List<SearchFilterDTO> filters,
    
    @JsonProperty("sort") String sort,
    
    @Min(0) @JsonProperty("offset") int offset,
    
    @Min(1) @Max(100) @JsonProperty("limit") int limit,
    
    @JsonProperty("user_context") UserContextDTO userContext

) {
  
  public SearchRequestDTO(String query) {
    this(query, null, null, "RELEVANCE", 0, 20, null);
  }
  
  public static Builder builder() {
    return new Builder();
  }
  
  public static class Builder {
    private String query;
    private String categoryId;
    private List<SearchFilterDTO> filters;
    private String sort = "RELEVANCE";
    private int offset = 0;
    private int limit = 20;
    private UserContextDTO userContext;
    
    public Builder query(String query) {
      this.query = query;
      return this;
    }
    
    public Builder categoryId(String categoryId) {
      this.categoryId = categoryId;
      return this;
    }
    
    public Builder filters(List<SearchFilterDTO> filters) {
      this.filters = filters;
      return this;
    }
    
    public Builder sort(String sort) {
      this.sort = sort;
      return this;
    }
    
    public Builder offset(int offset) {
      this.offset = offset;
      return this;
    }
    
    public Builder limit(int limit) {
      this.limit = limit;
      return this;
    }
    
    public Builder userContext(UserContextDTO userContext) {
      this.userContext = userContext;
      return this;
    }
    
    public SearchRequestDTO build() {
      return new SearchRequestDTO(query, categoryId, filters, sort, offset, limit, userContext);
    }
  }
}