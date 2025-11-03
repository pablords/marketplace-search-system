package com.marketplace.search.interfaces.rest.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO para marca (implementado como um Record)
 */
public record BrandDTO(

    @NotNull @NotBlank @JsonProperty("id") String id,

    @NotNull @NotBlank @JsonProperty("name") String name,

    @JsonProperty("description") String description

) {
  /**
   * Sobrescrevemos o toString() para manter o formato exato da classe original,
   * que omitia o campo 'description'.
   *
   * Nota: Se você quisesse o toString() padrão do record,
   * que inclui todos os campos, basta remover este método.
   */
  @Override
  public String toString() {
    return "BrandDTO{" +
        "id='" + id + '\'' +
        ", name='" + name + '\'' +
        '}';
  }
}