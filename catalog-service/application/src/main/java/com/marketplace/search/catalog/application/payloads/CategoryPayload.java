package com.marketplace.search.catalog.application.payloads;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CategoryPayload(
    @NotNull @NotBlank @JsonProperty("id") String id,

    @NotNull @NotBlank @JsonProperty("name") String name,

    @JsonProperty("parent_id") String parentId,

    @NotNull @JsonProperty("path") String path) {

}
