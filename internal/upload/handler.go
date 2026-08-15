package upload

import (
	"net/http"

	"github.com/autoparts/inventory-management/internal/api"
)

type Handler struct {
	svc *Service
}

func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc}
}

type presignRequest struct {
	Filename    string `json:"filename"`
	ContentType string `json:"contentType"`
}

func (h *Handler) Presign(w http.ResponseWriter, r *http.Request) {
	userID, ok := api.RequireUser(w, r)
	if !ok {
		return
	}
	var req presignRequest
	if err := api.DecodeJSON(r, &req); err != nil {
		api.FromError(w, err)
		return
	}
	out, err := h.svc.Presign(r.Context(), userID, req.Filename, req.ContentType)
	if err != nil {
		api.FromError(w, err)
		return
	}
	api.OK(w, out)
}
