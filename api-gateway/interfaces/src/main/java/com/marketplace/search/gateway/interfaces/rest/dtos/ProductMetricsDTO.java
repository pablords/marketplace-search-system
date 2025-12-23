package com.marketplace.search.gateway.interfaces.rest.dtos;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductMetricsDTO(
		@JsonProperty("total_views") Integer totalViews,
		@JsonProperty("total_sales") Integer totalSales,
		@JsonProperty("total_reviews") Integer totalReviews,
		@JsonProperty("average_rating") Double averageRating,
		@JsonProperty("stock_quantity") Integer stockQuantity,
		@JsonProperty("last_sale") Instant lastSale,
		@JsonProperty("last_view") Instant lastView,
		@JsonProperty("popularity") Integer popularity,
		@JsonProperty("quality") Double quality,
		@JsonProperty("ctr") Double ctr) {

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private Integer totalViews;
		private Integer totalSales;
		private Integer totalReviews;
		private Double averageRating;
		private Integer stockQuantity;
		private Instant lastSale;
		private Instant lastView;
		private Integer popularity;
		private Double quality;
		private Double ctr;

		public Builder totalViews(Integer totalViews) {
			this.totalViews = totalViews;
			return this;
		}

		public Builder totalSales(Integer totalSales) {
			this.totalSales = totalSales;
			return this;
		}

		public Builder totalReviews(Integer totalReviews) {
			this.totalReviews = totalReviews;
			return this;
		}

		public Builder averageRating(Double averageRating) {
			this.averageRating = averageRating;
			return this;
		}

		public Builder stockQuantity(Integer stockQuantity) {
			this.stockQuantity = stockQuantity;
			return this;
		}

		public Builder lastSale(Instant lastSale) {
			this.lastSale = lastSale;
			return this;
		}

		public Builder lastView(Instant lastView) {
			this.lastView = lastView;
			return this;
		}

		public Builder popularity(Integer popularity) {
			this.popularity = popularity;
			return this;
		}

		public Builder quality(Double quality) {
			this.quality = quality;
			return this;
		}

		public Builder ctr(Double ctr) {
			this.ctr = ctr;
			return this;
		}

		public ProductMetricsDTO build() {
			return new ProductMetricsDTO(
					totalViews,
					totalSales,
					totalReviews,
					averageRating,
					stockQuantity,
					lastSale,
					lastView,
					popularity,
					quality,
					ctr);
		}
	}

}