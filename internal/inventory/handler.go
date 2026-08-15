package inventory

import (
	"net/http"
	"strconv"

	"github.com/go-playground/validator/v10"

	"github.com/autoparts/inventory-management/internal/api"
	"github.com/autoparts/inventory-management/internal/model"
)

type Handler struct {
	svc      *Service
	validate *validator.Validate
}

func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc, validate: validator.New()}
}

func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	userID, ok := api.RequireUser(w, r)
	if !ok {
		return
	}
	var vehicle *model.VehicleCategory
	if v := r.URL.Query().Get("vehicle"); v != "" {
		cat := model.VehicleCategory(v)
		vehicle = &cat
	}
	items, err := h.svc.List(r.Context(), userID, r.URL.Query().Get("q"), vehicle, r.URL.Query().Get("status"))
	if err != nil {
		api.FromError(w, err)
		return
	}
	api.OK(w, items)
}

func (h *Handler) Add(w http.ResponseWriter, r *http.Request) {
	userID, ok := api.RequireUser(w, r)
	if !ok {
		return
	}
	var req AddPartRequest
	if err := api.DecodeJSON(r, &req); err != nil {
		api.FromError(w, err)
		return
	}
	if err := h.validate.Struct(req); err != nil {
		api.FromError(w, err)
		return
	}
	item, err := h.svc.Add(r.Context(), userID, req)
	if err != nil {
		api.FromError(w, err)
		return
	}
	api.Created(w, item)
}

func (h *Handler) Get(w http.ResponseWriter, r *http.Request) {
	userID, ok := api.RequireUser(w, r)
	if !ok {
		return
	}
	id, err := api.PathUUID(r, "id")
	if err != nil {
		api.FromError(w, err)
		return
	}
	item, err := h.svc.Get(r.Context(), userID, id)
	if err != nil {
		api.FromError(w, err)
		return
	}
	api.OK(w, item)
}

func (h *Handler) Update(w http.ResponseWriter, r *http.Request) {
	userID, ok := api.RequireUser(w, r)
	if !ok {
		return
	}
	id, err := api.PathUUID(r, "id")
	if err != nil {
		api.FromError(w, err)
		return
	}
	var req UpdatePartRequest
	if err := api.DecodeJSON(r, &req); err != nil {
		api.FromError(w, err)
		return
	}
	item, err := h.svc.Update(r.Context(), userID, id, req)
	if err != nil {
		api.FromError(w, err)
		return
	}
	api.OK(w, item)
}

func (h *Handler) Delete(w http.ResponseWriter, r *http.Request) {
	userID, ok := api.RequireUser(w, r)
	if !ok {
		return
	}
	id, err := api.PathUUID(r, "id")
	if err != nil {
		api.FromError(w, err)
		return
	}
	if err := h.svc.Delete(r.Context(), userID, id); err != nil {
		api.FromError(w, err)
		return
	}
	api.OK(w, nil)
}

func (h *Handler) UpdateQuantity(w http.ResponseWriter, r *http.Request) {
	userID, ok := api.RequireUser(w, r)
	if !ok {
		return
	}
	id, err := api.PathUUID(r, "id")
	if err != nil {
		api.FromError(w, err)
		return
	}
	var req QuantityUpdateRequest
	if err := api.DecodeJSON(r, &req); err != nil {
		api.FromError(w, err)
		return
	}
	item, err := h.svc.UpdateQuantity(r.Context(), userID, id, req)
	if err != nil {
		api.FromError(w, err)
		return
	}
	api.OK(w, item)
}

func (h *Handler) LowStock(w http.ResponseWriter, r *http.Request) {
	userID, ok := api.RequireUser(w, r)
	if !ok {
		return
	}
	items, err := h.svc.LowStock(r.Context(), userID)
	if err != nil {
		api.FromError(w, err)
		return
	}
	api.OK(w, items)
}

func (h *Handler) History(w http.ResponseWriter, r *http.Request) {
	userID, ok := api.RequireUser(w, r)
	if !ok {
		return
	}
	id, err := api.PathUUID(r, "id")
	if err != nil {
		api.FromError(w, err)
		return
	}
	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	rows, total, err := h.svc.History(r.Context(), userID, id, page, limit)
	if err != nil {
		api.FromError(w, err)
		return
	}
	api.OK(w, map[string]any{
		"content": rows,
		"page":    page,
		"limit":   limit,
		"total":   total,
	})
}
