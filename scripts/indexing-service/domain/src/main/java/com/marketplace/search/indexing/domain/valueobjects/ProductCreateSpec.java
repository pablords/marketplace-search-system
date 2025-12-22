package com.marketplace.search.indexing.domain.valueobjects;

import java.time.Instant;
import java.util.Objects;

import com.marketplace.search.indexing.domain.entities.Product;
import com.marketplace.search.indexing.domain.entities.Seller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Especificação de criação de Product usada pela camada de domínio.
 * A camada Application mapeia Command/DTO -> ProductCreateSpec e passa para o
 * DomainService.
 */
public final class ProductCreateSpec {

	@NotNull
	@Valid
	private final ProductInfo info;

	@NotNull
	@Valid
	private final Seller seller;

	@NotNull
	@Valid
	private final ProductMetrics metrics;

	@NotNull
	private final ProductStatus status;

	private ProductCreateSpec(Builder b) {
		this.info = Objects.requireNonNull(b.info);
		this.seller = Objects.requireNonNull(b.seller);
		this.metrics = Objects.requireNonNull(b.metrics);
		this.status = Objects.requireNonNull(b.status);
	}

	public static Builder builder() {
		return new Builder();
	}

	public ProductInfo getInfo() {
		return info;
	}

	public Seller getSeller() {
		return seller;
	}

	public ProductMetrics getMetrics() {
		return metrics;
	}

	public ProductStatus getStatus() {
		return status;
	}

	/**
	 * Converte esta spec em uma entidade Product. O ProductId e timestamps devem
	 * ser providos pela camada que gera o id (ex: serviço de domínio).
	 */
	public Product toProduct(ProductId id, Instant now) {
		return Product.builder()
				.id(id)
				.info(this.info)
				.seller(this.seller)
				.metrics(this.metrics)
				.status(this.status)
				.createdAt(now)
				.updatedAt(now)
				.build();
	}

	public static final class Builder {
		private ProductInfo info;
		private Seller seller;
		private ProductMetrics metrics;
		private ProductStatus status = ProductStatus.active(true);

		public Builder info(ProductInfo info) {
			this.info = info;
			return this;
		}

		public Builder seller(Seller seller) {
			this.seller = seller;
			return this;
		}

		public Builder metrics(ProductMetrics metrics) {
			this.metrics = metrics;
			return this;
		}

		public Builder status(ProductStatus status) {
			this.status = status;
			return this;
		}

		public ProductCreateSpec build() {
			return new ProductCreateSpec(this);
		}
	}
}