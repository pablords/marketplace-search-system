package com.marketplace.search.search.domain.valueobjects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Value Object representando uma marca
 */
public record Brand(
    @NotNull @NotBlank String id,
    @NotNull @NotBlank String name,
    String description
) {
    public Brand {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Brand ID cannot be null or empty");
        }
        id = id.trim();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Brand name cannot be null or empty");
        }
        name = name.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Brand brand = (Brand) o;
        return id.equals(brand.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Brand{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}

