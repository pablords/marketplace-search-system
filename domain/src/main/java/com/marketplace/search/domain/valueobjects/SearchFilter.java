package com.marketplace.search.domain.valueobjects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Value Object representando um filtro de busca
 */
public class SearchFilter {
    
    @NotNull
    @NotBlank
    private final String name;
    
    @NotNull
    private final FilterType type;
    
    @NotNull
    private final List<String> values;

    public SearchFilter(String name, FilterType type, List<String> values) {
        this.name = validateName(name);
        this.type = Objects.requireNonNull(type, "Filter type cannot be null");
        this.values = validateValues(values);
    }

    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Filter name cannot be null or empty");
        }
        return name.trim();
    }

    private List<String> validateValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Filter values cannot be null or empty");
        }
        return List.copyOf(values);
    }

    public static SearchFilter priceRange(String min, String max) {
        return new SearchFilter("price", FilterType.RANGE, List.of(min, max));
    }

    public static SearchFilter brand(String brandName) {
        return new SearchFilter("brand", FilterType.TERM, List.of(brandName));
    }

    public static SearchFilter category(String categoryId) {
        return new SearchFilter("category", FilterType.TERM, List.of(categoryId));
    }

    public static SearchFilter attributes(List<String> attributeValues) {
        return new SearchFilter("attributes", FilterType.TERMS, attributeValues);
    }

    public static SearchFilter condition(ProductCondition condition) {
        return new SearchFilter("condition", FilterType.TERM, List.of(condition.name()));
    }

    public static SearchFilter freeShipping() {
        return new SearchFilter("free_shipping", FilterType.BOOLEAN, List.of("true"));
    }

    public boolean isRangeFilter() {
        return type == FilterType.RANGE;
    }

    public boolean isBooleanFilter() {
        return type == FilterType.BOOLEAN;
    }

    public String getSingleValue() {
        if (values.size() != 1) {
            throw new IllegalStateException("Filter has multiple values, cannot get single value");
        }
        return values.get(0);
    }

    // Getters
    public String getName() { return name; }
    public FilterType getType() { return type; }
    public List<String> getValues() { return values; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchFilter that = (SearchFilter) o;
        return Objects.equals(name, that.name) &&
               type == that.type &&
               Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, values);
    }

    @Override
    public String toString() {
        return "SearchFilter{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", values=" + values +
                '}';
    }
}