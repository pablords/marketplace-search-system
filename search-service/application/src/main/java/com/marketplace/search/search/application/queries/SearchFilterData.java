package com.marketplace.search.search.application.queries;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SearchFilterData(
    @JsonProperty("name") String name,

    @JsonProperty("type") String type,

    @JsonProperty("values") List<String> values) {

}

