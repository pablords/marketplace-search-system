package com.marketplace.search.infrastructure.elasticsearch.documents;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Documento para marca no Elasticsearch
 */
public class BrandDocument {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("description")
    private String description;

    // Constructors
    public BrandDocument() {}

    public BrandDocument(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public BrandDocument(String id, String name, String description) {
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
}