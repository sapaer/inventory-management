package api

import (
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-playground/validator/v10"
	"github.com/google/uuid"

	"github.com/autoparts/inventory-management/internal/apperr"
)

type Envelope struct {
	Success bool       `json:"success"`
	Data    any        `json:"data"`
	Error   *ErrorInfo `json:"error"`
	Meta    *MetaInfo  `json:"meta,omitempty"`
}

type ErrorInfo struct {
	Code    string  `json:"code"`
	Message string  `json:"message"`
	Field   *string `json:"field,omitempty"`
}

type MetaInfo struct {
	Page  int   `json:"page"`
	Limit int   `json:"limit"`
	Total int64 `json:"total"`
}

func OK(w http.ResponseWriter, data any) {
	write(w, http.StatusOK, Envelope{Success: true, Data: data})
}

func Created(w http.ResponseWriter, data any) {
	write(w, http.StatusCreated, Envelope{Success: true, Data: data})
}

func FromError(w http.ResponseWriter, err error) {
	var ae *apperr.AppError
	if errors.As(err, &ae) {
		write(w, ae.HTTPStatus, Envelope{
			Success: false,
			Error:   &ErrorInfo{Code: ae.Code, Message: ae.Message},
		})
		return
	}

	var ve validator.ValidationErrors
	if errors.As(err, &ve) && len(ve) > 0 {
		field := ve[0].Field()
		write(w, http.StatusBadRequest, Envelope{
			Success: false,
			Error: &ErrorInfo{
				Code:    "VALIDATION_ERROR",
				Message: ve[0].Error(),
				Field:   &field,
			},
		})
		return
	}

	slog.Error("unhandled error", "err", err)
	write(w, http.StatusInternalServerError, Envelope{
		Success: false,
		Error:   &ErrorInfo{Code: "SERVER_ERROR", Message: "An unexpected error occurred"},
	})
}

func DecodeJSON(r *http.Request, dst any) error {
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(dst); err != nil {
		return apperr.BadRequest("VALIDATION_ERROR", "Invalid request body")
	}
	return nil
}

func PathUUID(r *http.Request, key string) (uuid.UUID, error) {
	id, err := uuid.Parse(chi.URLParam(r, key))
	if err != nil {
		return uuid.Nil, apperr.BadRequest("VALIDATION_ERROR", "Invalid "+key)
	}
	return id, nil
}

func write(w http.ResponseWriter, status int, body Envelope) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}
