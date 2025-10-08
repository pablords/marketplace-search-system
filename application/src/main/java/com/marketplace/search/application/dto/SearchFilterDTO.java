package com.marketplace.search.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO para filtro de busca
 */
public class SearchFilterDTO {
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("values")
    private List<String> values;

    // Constructors
    public SearchFilterDTO() {}

    public SearchFilterDTO(String name, String type, List<String> values) {
        this.name = name;
        this.type = type;
        this.values = values;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<String> getValues() { return values; }
    public void setValues(List<String> values) { this.values = values; }

    @Override
    public String toString() {
        return "SearchFilterDTO{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", values=" + values +
                '}';
    }
}