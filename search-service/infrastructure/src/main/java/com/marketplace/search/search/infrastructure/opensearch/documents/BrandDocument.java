package com.marketplace.search.search.infrastructure.opensearch.documents;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Documento para marca no OpenSearch
 */
public class BrandDocument {

	@JsonProperty("id")
	private String id;

	@JsonProperty("name")
	private String name;

	@JsonProperty("description")
	private String description;

	public BrandDocument() {
	}

	public BrandDocument(String id, String name, String description) {
		this.id = id;
		this.name = name;
		this.description = description;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}

