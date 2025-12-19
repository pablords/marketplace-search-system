package com.marketplace.search.gateway.interfaces.rest.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO para categoria
 */
public record CategoryDTO(
    @NotNull @NotBlank @JsonProperty("id") String id,

    @NotNull @NotBlank @JsonProperty("name") String name,

    @JsonProperty("parent_id") String parentId,

    @NotNull @JsonProperty("path") String path)

{
  @Override
  public String toString() {
    return "CategoryDTO{" +
        "id='" + id + '\'' +
        ", name='" + name + '\'' +
        ", path='" + path + '\'' +
        '}';
  }
}
