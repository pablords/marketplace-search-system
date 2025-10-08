package com.marketplace.search.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO para marca
 */
public class BrandDTO {
    
    @NotNull
    @NotBlank
    @JsonProperty("id")
    private String id;
    
    @NotNull
    @NotBlank
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("description")
    private String description;

    // Constructors
    public BrandDTO() {}

    public BrandDTO(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return "BrandDTO{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}