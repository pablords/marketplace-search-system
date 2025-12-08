package com.marketplace.search.catalog.infrastructure.persistence.entities;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "sellers")
@Data
public class SellerEntity {
	@Id
	@Column(name = "id", length = 255)
	private String id;

	@Column(name = "name", length = 255)
	private String name;

	@Column(name = "type", length = 50)
	private String type;

	@Column(name = "status", length = 50)
	private String status;

	@Column(name = "score", precision = 5, scale = 2)
	private BigDecimal score;

	@Column(name = "positive_reviews")
	private Integer positiveReviews;
	@Column(name = "negative_reviews")
	private Integer negativeReviews;
	@Column(name = "neutral_reviews")
	private Integer neutralReviews;
	@Column(name = "total_reviews")
	private Integer totalReviews;
	// Métricas de reputação ficam aqui
	@Column(name = "cancellation_rate", precision = 5, scale = 2)
	private BigDecimal cancellationRate;

	@Column(name = "delivery_performance", precision = 5, scale = 2)
	private BigDecimal deliveryPerformance;

}
