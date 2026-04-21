package com.marketplace.search.search.domain.valueobjects;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.marketplace.search.search.domain.entities.Category;

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
    int limit,
    boolean rankingDebug
) {
    public SearchQuery {
        if (terms == null || terms.trim().isEmpty()) {
            throw new IllegalArgumentException("Search terms cannot be null or empty");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative");
        }
        // Limite aumentado para 1000 para suportar busca de candidatos para ML ranking (Top 400)
        if (limit <= 0 || limit > 1000) {
            throw new IllegalArgumentException("Limit must be between 1 and 1000");
        }
        filters = filters != null ? List.copyOf(filters) : List.of();
        sort = sort != null ? sort : SearchSort.RELEVANCE;
    }

    /**
     * Extrai palavras-chave individuais da consulta
     */
    @JsonIgnore
    public Set<String> getKeywords() {
        return Arrays.stream(terms.replaceAll("[^a-zA-Z0-9\\s]", "").split("\\s+"))
                     .collect(Collectors.toSet());
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
    @JsonIgnore
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

