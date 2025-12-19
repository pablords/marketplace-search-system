package com.marketplace.search.search.infrastructure.opensearch.documents;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Documento para status do produto no OpenSearch
 */
public class ProductStatusDocument {

	@JsonProperty("is_active")
	private Boolean isActive;

	@JsonProperty("is_suspended")
	private Boolean isSuspended;

	@JsonProperty("has_stock")
	private Boolean hasStock;

	public ProductStatusDocument() {
	}

	public ProductStatusDocument(Boolean isActive, Boolean isSuspended, Boolean hasStock) {
		this.isActive = isActive;
		this.isSuspended = isSuspended;
		this.hasStock = hasStock;
	}

	public Boolean getIsActive() {
		return isActive;
	}

	public void setIsActive(Boolean isActive) {
		this.isActive = isActive;
	}

	public Boolean getIsSuspended() {
		return isSuspended;
	}

	public void setIsSuspended(Boolean isSuspended) {
		this.isSuspended = isSuspended;
	}

	public Boolean getHasStock() {
		return hasStock;
	}

	public void setHasStock(Boolean hasStock) {
		this.hasStock = hasStock;
	}
}

