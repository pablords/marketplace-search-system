package com.marketplace.search.domain.valueobjects;

import java.util.List;
import java.util.Set;

import com.marketplace.search.domain.entities.Category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Value Object representando uma consulta de busca
 */
public record SearchQuery(
    @NotNull @NotBlank String terms,
    Category category,
    List<SearchFilter> filters,
    SearchSort sort,
    int offset,
    int limit
) {
    public SearchQuery {
        if (terms == null || terms.trim().isEmpty()) {
            throw new IllegalArgumentException("Search terms cannot be null or empty");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative");
        }
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }
        filters = filters != null ? List.copyOf(filters) : List.of();
        sort = sort != null ? sort : SearchSort.RELEVANCE;
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
                .filter(filter -> filter.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Verifica se a consulta é específica de uma categoria
     */
    public boolean hasCategoryFilter() {
        return category != null;
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