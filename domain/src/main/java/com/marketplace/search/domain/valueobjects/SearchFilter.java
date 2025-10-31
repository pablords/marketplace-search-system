
package com.marketplace.search.domain.valueobjects;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Value Object representando um filtro de busca
 */
public record SearchFilter(
    @NotNull @NotBlank String name,
    @NotNull FilterType type,
    @NotNull List<String> values
) {
    public SearchFilter {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Filter name cannot be null or empty");
        }
        if (type == null) {
            throw new IllegalArgumentException("Filter type cannot be null");
        }
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Filter values cannot be null or empty");
        }
    }

    public static SearchFilter of(String name, FilterType type, List<String> values) {
        return new SearchFilter(name.trim(), type, List.copyOf(values));
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

    @Override
    public String toString() {
        return "SearchFilter{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", values=" + values +
                '}';
    }
}