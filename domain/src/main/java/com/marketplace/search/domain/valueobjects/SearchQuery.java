package com.marketplace.search.domain.valueobjects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Value Object representando uma consulta de busca
 */
public class SearchQuery {
    
    @NotNull
    @NotBlank
    private final String terms;
    
    private final Category category;
    
    private final List<SearchFilter> filters;
    
    private final SearchSort sort;
    
    private final int offset;
    
    private final int limit;

    public SearchQuery(String terms, Category category, List<SearchFilter> filters,
                      SearchSort sort, int offset, int limit) {
        this.terms = validateTerms(terms);
        this.category = category;
        this.filters = filters != null ? List.copyOf(filters) : List.of();
        this.sort = sort != null ? sort : SearchSort.RELEVANCE;
        this.offset = validateOffset(offset);
        this.limit = validateLimit(limit);
    }

    private String validateTerms(String terms) {
        if (terms == null || terms.trim().isEmpty()) {
            throw new IllegalArgumentException("Search terms cannot be null or empty");
        }
        return terms.trim().toLowerCase();
    }

    private int validateOffset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative");
        }
        return offset;
    }

    private int validateLimit(int limit) {
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }
        return limit;
    }

    /**
     * Extrai palavras-chave individuais da consulta
     */
    public Set<String> getKeywords() {
        return Set.of(terms.replaceAll("[^a-zA-Z0-9\\s]", "").split("\\s+"));
    }

    /**
     * Verifica se a consulta tem filtros aplicados
     */
    public boolean hasFilters() {
        return !filters.isEmpty();
    }

    /**
     * Obtém filtro por nome
     */
    public SearchFilter getFilter(String name) {
        return filters.stream()
                .filter(filter -> filter.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Verifica se a consulta é específica de uma categoria
     */
    public boolean hasCategoryFilter() {
        return category != null;
    }

    // Getters
    public String getTerms() { return terms; }
    public Category getCategory() { return category; }
    public List<SearchFilter> getFilters() { return filters; }
    public SearchSort getSort() { return sort; }
    public int getOffset() { return offset; }
    public int getLimit() { return limit; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchQuery that = (SearchQuery) o;
        return offset == that.offset &&
               limit == that.limit &&
               Objects.equals(terms, that.terms) &&
               Objects.equals(category, that.category) &&
               Objects.equals(filters, that.filters) &&
               sort == that.sort;
    }

    @Override
    public int hashCode() {
        return Objects.hash(terms, category, filters, sort, offset, limit);
    }

    @Override
    public String toString() {
        return "SearchQuery{" +
                "terms='" + terms + '\'' +
                ", category=" + category +
                ", filters=" + filters.size() +
                ", sort=" + sort +
                ", offset=" + offset +
                ", limit=" + limit +
                '}';
    }
}