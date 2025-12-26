package models

import (
	"time"
)

// ErrorResponse representa uma resposta de erro padronizada
type ErrorResponse struct {
	Timestamp time.Time         `json:"timestamp"`
	Status    int               `json:"status"`
	Error     string            `json:"error"`
	Message   string            `json:"message"`
	Path      string            `json:"path"`
	Details   map[string]string `json:"details,omitempty"`
}

