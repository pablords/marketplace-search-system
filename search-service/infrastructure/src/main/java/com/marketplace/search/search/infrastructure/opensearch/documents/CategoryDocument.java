package com.marketplace.search.search.infrastructure.opensearch.documents;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Documento para categoria no OpenSearch
 */
public class CategoryDocument {

	@JsonProperty("id")
	private String id;

	@JsonProperty("name")
	private String name;

	@JsonProperty("path")
	private String path;

	@JsonProperty("parent_id")
	private String parentId;

	public CategoryDocument() {
	}

	public CategoryDocument(String id, String name, String path, String parentId) {
		this.id = id;
		this.name = name;
		this.path = path;
		this.parentId = parentId;
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

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getParentId() {
		return parentId;
	}

	public void setParentId(String parentId) {
		this.parentId = parentId;
	}
}

