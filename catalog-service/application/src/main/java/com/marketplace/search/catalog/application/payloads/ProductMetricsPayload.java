package com.marketplace.search.catalog.application.payloads;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProductMetricsPayload(
		@JsonProperty("total_views") int totalViews,
		@JsonProperty("total_sales") int totalSales,
		@JsonProperty("total_reviews") int totalReviews,
		@JsonProperty("average_rating") double averageRating,
		@JsonProperty("stock_quantity") int stockQuantity,
		@JsonProperty("last_sale") Instant lastSale,
		@JsonProperty("last_view") Instant lastView,
		@JsonProperty("popularity") int popularity,
		@JsonProperty("quality") double quality,
		@JsonProperty("ctr") double ctr) {

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private int totalViews;
		private int totalSales;
		private int totalReviews;
		private double averageRating;
		private int stockQuantity;
		private double conversionRate;
		private Instant lastSale;
		private Instant lastView;
		private int popularity;
		private double quality;
		private double ctr;

		public Builder totalViews(int totalViews) {
			this.totalViews = totalViews;
			return this;
		}

		public Builder totalSales(int totalSales) {
			this.totalSales = totalSales;
			return this;
		}

		public Builder totalReviews(int totalReviews) {
			this.totalReviews = totalReviews;
			return this;
		}

		public Builder averageRating(double averageRating) {
			this.averageRating = averageRating;
			return this;
		}

		public Builder stockQuantity(int stockQuantity) {
			this.stockQuantity = stockQuantity;
			return this;
		}

		public Builder conversionRate(double conversionRate) {
			this.conversionRate = conversionRate;
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

		public Builder popularity(int popularity) {
			this.popularity = popularity;
			return this;
		}

		public Builder quality(double quality) {
			this.quality = quality;
			return this;
		}

		public Builder ctr(double ctr) {
			this.ctr = ctr;
			return this;
		}

		public ProductMetricsPayload build() {
			return new ProductMetricsPayload(
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