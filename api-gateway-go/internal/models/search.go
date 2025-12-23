package models

// SearchRequest representa uma requisição de busca
type SearchRequest struct {
	Query      string         `json:"query" validate:"required"`
	CategoryID *string         `json:"category_id,omitempty"`
	Filters    []SearchFilter `json:"filters,omitempty"`
	Sort       *string         `json:"sort,omitempty"`
	Offset     int            `json:"offset" validate:"min=0"`
	Limit      int            `json:"limit" validate:"min=1,max=100"`
	UserContext *UserContext  `json:"user_context,omitempty"`
}

// SearchResult representa o resultado de uma busca
type SearchResult struct {
	Products         []Product      `json:"products"`
	TotalCount       int64          `json:"total_count"`
	PageSize         int            `json:"page_size"`
	PageNumber       int            `json:"page_number"`
	TotalPages       int            `json:"total_pages"`
	HasNextPage      bool           `json:"has_next_page"`
	HasPreviousPage  bool           `json:"has_previous_page"`
	ExecutionTimeMs  int64          `json:"execution_time_ms"`
	Metrics          *SearchMetrics `json:"metrics,omitempty"`
}

// SearchFilter representa um filtro de busca
type SearchFilter struct {
	Name   *string  `json:"name,omitempty"`
	Type   *string  `json:"type,omitempty"`
	Values []string `json:"values,omitempty"`
}

// SearchMetrics representa métricas de busca
type SearchMetrics struct {
	QueriesPerSecond int    `json:"queries_per_second"`
	AverageScore      float64 `json:"average_score"`
	IndexedDocuments int    `json:"indexed_documents"`
	IndexSize        int64  `json:"index_size"`
	UsedCache        bool   `json:"used_cache"`
	ShardInfo        *string `json:"shard_info,omitempty"`
}

// UserContext representa o contexto do usuário
type UserContext struct {
	UserID            *string        `json:"user_id,omitempty"`
	Location          *UserLocation  `json:"location,omitempty"`
	PreferredCategories []string     `json:"preferred_categories,omitempty"`
	PurchaseHistory   []string       `json:"purchase_history,omitempty"`
	SearchHistory     []string       `json:"search_history,omitempty"`
	ViewHistory       []string       `json:"view_history,omitempty"`
}

// UserLocation representa a localização do usuário
type UserLocation struct {
	Country   *string  `json:"country,omitempty"`
	State     *string  `json:"state,omitempty"`
	City      *string  `json:"city,omitempty"`
	ZipCode   *string  `json:"zip_code,omitempty"`
	Latitude  *float64 `json:"latitude,omitempty"`
	Longitude *float64 `json:"longitude,omitempty"`
}

