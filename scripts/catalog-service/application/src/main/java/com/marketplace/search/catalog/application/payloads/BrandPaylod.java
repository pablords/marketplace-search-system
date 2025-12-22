package com.marketplace.search.catalog.application.payloads;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BrandPaylod(

    @NotNull @NotBlank @JsonProperty("id") String id,

    @NotNull @NotBlank @JsonProperty("name") String name,

    @JsonProperty("description") String description) {

}
