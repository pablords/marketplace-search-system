package com.marketplace.search.search.domain.valueobjects;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Objects;

/**
 * Value Object representando o score de relevância de um produto na busca
 */
public class SearchScore {
    
    @Min(0)
    @Max(1)
    private final double value;

    public SearchScore(double value) {
        this.value = validateValue(value);
    }

    private double validateValue(double value) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Search score must be between 0.0 and 1.0");
        }
        return value;
    }

    public static SearchScore zero() {
        return new SearchScore(0.0);
    }

    public static SearchScore max() {
        return new SearchScore(1.0);
    }

    public static SearchScore of(double value) {
        return new SearchScore(value);
    }

    public boolean isHighRelevance() {
        return value >= 0.8;
    }

    public boolean isMediumRelevance() {
        return value >= 0.5 && value < 0.8;
    }

    public boolean isLowRelevance() {
        return value < 0.5;
    }

    public SearchScore boost(double factor) {
        return new SearchScore(Math.min(1.0, value * factor));
    }

    public SearchScore penalty(double factor) {
        return new SearchScore(Math.max(0.0, value * (1.0 - factor)));
    }

    public double getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchScore that = (SearchScore) o;
        return Double.compare(that.value, value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "SearchScore{" + value + '}';
    }
}

