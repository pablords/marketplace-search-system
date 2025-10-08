package com.marketplace.search.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTO para requisição de busca
 */
public class SearchRequestDTO {
    
    @NotNull
    @NotBlank
    @JsonProperty("query")
    private String query;
    
    @JsonProperty("category_id")
    private String categoryId;
    
    @JsonProperty("filters")
    private List<SearchFilterDTO> filters;
    
    @JsonProperty("sort")
    private String sort = "RELEVANCE";
    
    @Min(0)
    @JsonProperty("offset")
    private int offset = 0;
    
    @Min(1)
    @Max(100)
    @JsonProperty("limit")
    private int limit = 20;
    
    @JsonProperty("user_context")
    private UserContextDTO userContext;

    // Constructors
    public SearchRequestDTO() {}

    public SearchRequestDTO(String query) {
        this.query = query;
    }

    // Getters and Setters
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public List<SearchFilterDTO> getFilters() { return filters; }
    public void setFilters(List<SearchFilterDTO> filters) { this.filters = filters; }

    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }

    public int getOffset() { return offset; }
    public void setOffset(int offset) { this.offset = offset; }

    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }

    public UserContextDTO getUserContext() { return userContext; }
    public void setUserContext(UserContextDTO userContext) { this.userContext = userContext; }

    @Override
    public String toString() {
        return "SearchRequestDTO{" +
                "query='" + query + '\'' +
                ", categoryId='" + categoryId + '\'' +
                ", sort='" + sort + '\'' +
                ", offset=" + offset +
                ", limit=" + limit +
                '}';
    }
}