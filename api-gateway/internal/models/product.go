package models

import (
	"time"
)

// RankingDebug representa informações de depuração do ranking
type RankingDebug struct {
	FinalScore float64            `json:"final_score"`
	Features   map[string]float64 `json:"features"`
}

// Product representa um produto no marketplace
type Product struct {
	ID             string          `json:"id" validate:"required"`
	Title          string          `json:"title" validate:"required"`
	Description    *string         `json:"description,omitempty"`
	Price          float64         `json:"price" validate:"required,gt=0"`
	Currency       string          `json:"currency" validate:"required"`
	Category       Category        `json:"category" validate:"required"`
	Brand          Brand           `json:"brand" validate:"required"`
	Seller         Seller          `json:"seller" validate:"required"`
	Images         []string        `json:"images,omitempty"`
	Attributes     []string        `json:"attributes,omitempty"`
	Tags           []string        `json:"tags,omitempty"`
	StockQuantity  *int            `json:"available_quantity,omitempty"`
	Condition      *string         `json:"condition,omitempty"`
	IsActive       *bool           `json:"is_active,omitempty"`
	ProductMetrics *ProductMetrics `json:"metrics,omitempty"`
	RankingDebug   *RankingDebug   `json:"ranking_debug,omitempty"`
}

// Category representa uma categoria de produto
type Category struct {
	ID       string  `json:"id" validate:"required"`
	Name     string  `json:"name" validate:"required"`
	ParentID *string `json:"parent_id,omitempty"`
	Path     string  `json:"path" validate:"required"`
}

// Brand representa uma marca
type Brand struct {
	ID          string  `json:"id" validate:"required"`
	Name        string  `json:"name" validate:"required"`
	Description *string `json:"description,omitempty"`
}

// Seller representa um vendedor
type Seller struct {
	ID         string            `json:"id" validate:"required"`
	Name       string            `json:"name" validate:"required"`
	Type       *string           `json:"type,omitempty"`
	Reputation *SellerReputation `json:"reputation,omitempty"`
	Status     *string           `json:"status,omitempty"`
	MemberSince *string          `json:"member_since,omitempty"`
}

// SellerReputation representa a reputação de um vendedor
type SellerReputation struct {
	Score               *float64 `json:"score,omitempty"`
	TotalReviews        *int     `json:"total_reviews,omitempty"`
	PositiveReviews     *int     `json:"positive_reviews,omitempty"`
	NeutralReviews      *int     `json:"neutral_reviews,omitempty"`
	NegativeReviews     *int     `json:"negative_reviews,omitempty"`
	CancellationRate    *float64 `json:"cancellation_rate,omitempty"`
	DeliveryPerformance *float64 `json:"delivery_performance,omitempty"`
}

// ProductMetrics representa métricas de um produto
type ProductMetrics struct {
	TotalViews    *int       `json:"total_views,omitempty"`
	TotalSales    *int       `json:"total_sales,omitempty"`
	TotalReviews  *int       `json:"total_reviews,omitempty"`
	AverageRating *float64   `json:"average_rating,omitempty"`
	StockQuantity *int       `json:"stock_quantity,omitempty"`
	LastSale      *time.Time `json:"last_sale,omitempty"`
	LastView      *time.Time `json:"last_view,omitempty"`
	Popularity    *int       `json:"popularity,omitempty"`
	Quality       *float64   `json:"quality,omitempty"`
	CTR           *float64   `json:"ctr,omitempty"`
}

