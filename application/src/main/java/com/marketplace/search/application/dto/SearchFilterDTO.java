package com.marketplace.search.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO para filtro de busca
 */
public record SearchFilterDTO(
    
    @JsonProperty("name") String name,
    
    @JsonProperty("type") String type,
    
    @JsonProperty("values") List<String> values

) {}