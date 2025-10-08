package com.marketplace.search.domain.valueobjects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

/**
 * Value Object representando uma categoria de produto
 */
public class Category {
    
    @NotNull
    @NotBlank
    private final String id;
    
    @NotNull
    @NotBlank
    private final String name;
    
    private final String parentId;
    
    @NotNull
    private final String path; // ex: "eletronicos/celulares/smartphones"

    public Category(String id, String name, String parentId, String path) {
        this.id = validateId(id);
        this.name = validateName(name);
        this.parentId = parentId;
        this.path = validatePath(path);
    }

    private String validateId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Category ID cannot be null or empty");
        }
        return id.trim();
    }

    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name cannot be null or empty");
        }
        return name.trim();
    }

    private String validatePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Category path cannot be null or empty");
        }
        return path.trim().toLowerCase();
    }

    public boolean isSubcategoryOf(Category other) {
        return this.path.startsWith(other.path + "/");
    }

    public int getDepth() {
        return path.split("/").length;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getParentId() { return parentId; }
    public String getPath() { return path; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Category category = (Category) o;
        return Objects.equals(id, category.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Category{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", path='" + path + '\'' +
                '}';
    }
}