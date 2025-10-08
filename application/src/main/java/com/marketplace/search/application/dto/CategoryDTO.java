package com.marketplace.search.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO para categoria
 */
public class CategoryDTO {
    
    @NotNull
    @NotBlank
    @JsonProperty("id")
    private String id;
    
    @NotNull
    @NotBlank
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("parent_id")
    private String parentId;
    
    @NotNull
    @JsonProperty("path")
    private String path;

    // Constructors
    public CategoryDTO() {}

    public CategoryDTO(String id, String name, String parentId, String path) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.path = path;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    @Override
    public String toString() {
        return "CategoryDTO{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", path='" + path + '\'' +
                '}';
    }
}