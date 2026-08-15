package notification

import (
	"net/http"
	"strconv"

	"github.com/autoparts/inventory-management/internal/api"
)

type Handler struct {
	svc *Service
}

func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc}
}

func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	userID, ok := api.RequireUser(w, r)
	if !ok {
		return
	}
	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	rows, total, err := h.svc.List(r.Context(), userID, page, limit)
	if err != nil {
		api.FromError(w, err)
		return
	}
	api.OK(w, map[string]any{"content": rows, "page": page, "limit": limit, "total": total})
}

func (h *Handler) MarkRead(w http.ResponseWriter, r *http.Request) {
	userID, ok := api.RequireUser(w, r)
	if !ok {
		return
	}
	id, err := api.PathUUID(r, "id")
	if err != nil {
		api.FromError(w, err)
		return
	}
	dto, err := h.svc.MarkRead(r.Context(), userID, id)
	if err != nil {
		api.FromError(w, err)
		return
	}
	api.OK(w, dto)
}
