package inventory

import (
	"context"
	"strings"

	"github.com/google/uuid"

	"github.com/autoparts/inventory-management/internal/apperr"
	"github.com/autoparts/inventory-management/internal/model"
)

type Store interface {
	ListActiveByUser(ctx context.Context, userID uuid.UUID) ([]model.InventoryItem, error)
	FullTextSearch(ctx context.Context, userID uuid.UUID, query string) ([]model.InventoryItem, error)
	FindByID(ctx context.Context, id uuid.UUID) (*model.InventoryItem, error)
	ExistsByPartName(ctx context.Context, userID uuid.UUID, partName string) (bool, error)
	Create(ctx context.Context, item *model.InventoryItem) (*model.InventoryItem, error)
	Update(ctx context.Context, item *model.InventoryItem) (*model.InventoryItem, error)
	FindLowStockByUser(ctx context.Context, userID uuid.UUID) ([]model.InventoryItem, error)
	FindAllLowStockActive(ctx context.Context) ([]model.InventoryItem, error)
	InsertHistory(ctx context.Context, h *model.InventoryHistory) error
	ListHistory(ctx context.Context, itemID uuid.UUID, limit, offset int) ([]model.InventoryHistory, int64, error)
}

type LowStockNotifier interface {
	TriggerLowStockCheck(ctx context.Context, item model.InventoryItem) error
}

type Service struct {
	store     Store
	notifier  LowStockNotifier
}

func NewService(store Store, notifier LowStockNotifier) *Service {
	return &Service{store: store, notifier: notifier}
}

type AddPartRequest struct {
	PartName        string                `json:"partName" validate:"required,max=200"`
	LocalName       *string               `json:"localName"`
	Specification   *string               `json:"specification"`
	Description     *string               `json:"description"`
	VehicleCategory model.VehicleCategory `json:"vehicleCategory" validate:"required"`
	Brand           *string               `json:"brand"`
	Model           *string               `json:"model"`
	Quantity        *int                  `json:"quantity" validate:"required,gte=0"`
	MinQuantity     *int                  `json:"minQuantity"`
	SellingPrice    *float64              `json:"sellingPrice"`
	CostPrice       *float64              `json:"costPrice"`
	Images          []string              `json:"images" validate:"max=3"`
}

type UpdatePartRequest struct {
	PartName        *string                `json:"partName"`
	LocalName       *string                `json:"localName"`
	Specification   *string                `json:"specification"`
	Description     *string                `json:"description"`
	VehicleCategory *model.VehicleCategory `json:"vehicleCategory"`
	Brand           *string                `json:"brand"`
	Model           *string                `json:"model"`
	MinQuantity     *int                   `json:"minQuantity"`
	SellingPrice    *float64               `json:"sellingPrice"`
	CostPrice       *float64               `json:"costPrice"`
	Images          []string               `json:"images" validate:"max=3"`
}

type QuantityUpdateRequest struct {
	Change     int              `json:"change" validate:"required"`
	ChangeType model.ChangeType `json:"changeType"`
	Note       *string          `json:"note"`
}

type ItemResponse struct {
	ID              uuid.UUID              `json:"id"`
	PartName        string                 `json:"partName"`
	LocalName       *string                `json:"localName"`
	Specification   *string                `json:"specification"`
	Description     *string                `json:"description"`
	VehicleCategory *model.VehicleCategory `json:"vehicleCategory"`
	Brand           *string                `json:"brand"`
	Model           *string                `json:"model"`
	Quantity        int                    `json:"quantity"`
	MinQuantity     int                    `json:"minQuantity"`
	SellingPrice    *float64               `json:"sellingPrice"`
	Images          []string               `json:"images"`
	StockStatus     string                 `json:"stockStatus"`
	IsActive        bool                   `json:"isActive"`
	IsDuplicate     *bool                  `json:"isDuplicate,omitempty"`
	CreatedAt       any                    `json:"createdAt"`
	UpdatedAt       any                    `json:"updatedAt"`
}

type HistoryResponse struct {
	ID         uuid.UUID        `json:"id"`
	ItemID     uuid.UUID        `json:"itemId"`
	ChangeType model.ChangeType `json:"changeType"`
	QtyBefore  int              `json:"qtyBefore"`
	QtyChange  int              `json:"qtyChange"`
	QtyAfter   int              `json:"qtyAfter"`
	Note       *string          `json:"note"`
	CreatedAt  any              `json:"createdAt"`
}

func (s *Service) List(ctx context.Context, userID uuid.UUID, q string, vehicle *model.VehicleCategory, status string) ([]ItemResponse, error) {
	var items []model.InventoryItem
	var err error
	if strings.TrimSpace(q) != "" {
		items, err = s.store.FullTextSearch(ctx, userID, q)
	} else {
		items, err = s.store.ListActiveByUser(ctx, userID)
	}
	if err != nil {
		return nil, err
	}

	out := make([]ItemResponse, 0, len(items))
	for _, item := range items {
		if vehicle != nil && (item.VehicleCategory == nil || *item.VehicleCategory != *vehicle) {
			continue
		}
		if !applyStockFilter(item, status) {
			continue
		}
		out = append(out, toResponse(item, nil))
	}
	return out, nil
}

func (s *Service) Get(ctx context.Context, userID, itemID uuid.UUID) (*ItemResponse, error) {
	item, err := s.requireOwnedActive(ctx, userID, itemID)
	if err != nil {
		return nil, err
	}
	resp := toResponse(*item, nil)
	return &resp, nil
}

func (s *Service) Add(ctx context.Context, userID uuid.UUID, req AddPartRequest) (*ItemResponse, error) {
	if !req.VehicleCategory.Valid() {
		return nil, apperr.BadRequest("VALIDATION_ERROR", "Invalid vehicle category")
	}
	dup, err := s.store.ExistsByPartName(ctx, userID, req.PartName)
	if err != nil {
		return nil, err
	}
	minQty := 2
	if req.MinQuantity != nil {
		minQty = *req.MinQuantity
	}
	qty := 0
	if req.Quantity != nil {
		qty = *req.Quantity
	}
	saved, err := s.store.Create(ctx, &model.InventoryItem{
		UserID:          userID,
		PartName:        req.PartName,
		LocalName:       req.LocalName,
		Specification:   req.Specification,
		Description:     req.Description,
		VehicleCategory: &req.VehicleCategory,
		Brand:           req.Brand,
		Model:           req.Model,
		Quantity:        qty,
		MinQuantity:     minQty,
		SellingPrice:    req.SellingPrice,
		CostPrice:       req.CostPrice,
		Images:          req.Images,
	})
	if err != nil {
		return nil, err
	}
	note := "Initial stock"
	if err := s.store.InsertHistory(ctx, &model.InventoryHistory{
		ItemID:     saved.ID,
		UserID:     userID,
		ChangeType: model.ChangeAdd,
		QtyBefore:  0,
		QtyChange:  qty,
		QtyAfter:   qty,
		Note:       &note,
	}); err != nil {
		return nil, err
	}
	resp := toResponse(*saved, &dup)
	return &resp, nil
}

func (s *Service) Update(ctx context.Context, userID, itemID uuid.UUID, req UpdatePartRequest) (*ItemResponse, error) {
	item, err := s.requireOwnedActive(ctx, userID, itemID)
	if err != nil {
		return nil, err
	}
	if req.PartName != nil {
		item.PartName = *req.PartName
	}
	if req.LocalName != nil {
		item.LocalName = req.LocalName
	}
	if req.Specification != nil {
		item.Specification = req.Specification
	}
	if req.Description != nil {
		item.Description = req.Description
	}
	if req.VehicleCategory != nil {
		item.VehicleCategory = req.VehicleCategory
	}
	if req.Brand != nil {
		item.Brand = req.Brand
	}
	if req.Model != nil {
		item.Model = req.Model
	}
	if req.MinQuantity != nil {
		item.MinQuantity = *req.MinQuantity
	}
	if req.SellingPrice != nil {
		item.SellingPrice = req.SellingPrice
	}
	if req.CostPrice != nil {
		item.CostPrice = req.CostPrice
	}
	if req.Images != nil {
		item.Images = req.Images
	}
	saved, err := s.store.Update(ctx, item)
	if err != nil {
		return nil, err
	}
	resp := toResponse(*saved, nil)
	return &resp, nil
}

func (s *Service) UpdateQuantity(ctx context.Context, userID, itemID uuid.UUID, req QuantityUpdateRequest) (*ItemResponse, error) {
	item, err := s.requireOwnedActive(ctx, userID, itemID)
	if err != nil {
		return nil, err
	}
	before := item.Quantity
	after := before + req.Change
	if after < 0 {
		return nil, apperr.Conflict("INSUFFICIENT_STOCK", "Quantity cannot go below zero")
	}
	item.Quantity = after
	saved, err := s.store.Update(ctx, item)
	if err != nil {
		return nil, err
	}
	changeType := req.ChangeType
	if changeType == "" {
		changeType = model.ChangeAdjustment
	}
	if err := s.store.InsertHistory(ctx, &model.InventoryHistory{
		ItemID:     itemID,
		UserID:     userID,
		ChangeType: changeType,
		QtyBefore:  before,
		QtyChange:  req.Change,
		QtyAfter:   after,
		Note:       req.Note,
	}); err != nil {
		return nil, err
	}
	if after <= saved.MinQuantity && s.notifier != nil {
		_ = s.notifier.TriggerLowStockCheck(ctx, *saved)
	}
	resp := toResponse(*saved, nil)
	return &resp, nil
}

func (s *Service) Delete(ctx context.Context, userID, itemID uuid.UUID) error {
	item, err := s.store.FindByID(ctx, itemID)
	if err != nil {
		return err
	}
	if item == nil || item.UserID != userID {
		return apperr.NotFound("Part not found")
	}
	item.IsActive = false
	_, err = s.store.Update(ctx, item)
	return err
}

func (s *Service) LowStock(ctx context.Context, userID uuid.UUID) ([]ItemResponse, error) {
	items, err := s.store.FindLowStockByUser(ctx, userID)
	if err != nil {
		return nil, err
	}
	out := make([]ItemResponse, 0, len(items))
	for _, item := range items {
		out = append(out, toResponse(item, nil))
	}
	return out, nil
}

func (s *Service) History(ctx context.Context, userID, itemID uuid.UUID, page, limit int) ([]HistoryResponse, int64, error) {
	item, err := s.store.FindByID(ctx, itemID)
	if err != nil {
		return nil, 0, err
	}
	if item == nil || item.UserID != userID {
		return nil, 0, apperr.NotFound("Part not found")
	}
	if page < 1 {
		page = 1
	}
	if limit <= 0 {
		limit = 20
	}
	rows, total, err := s.store.ListHistory(ctx, itemID, limit, (page-1)*limit)
	if err != nil {
		return nil, 0, err
	}
	out := make([]HistoryResponse, 0, len(rows))
	for _, h := range rows {
		out = append(out, HistoryResponse{
			ID: h.ID, ItemID: h.ItemID, ChangeType: h.ChangeType,
			QtyBefore: h.QtyBefore, QtyChange: h.QtyChange, QtyAfter: h.QtyAfter,
			Note: h.Note, CreatedAt: h.CreatedAt,
		})
	}
	return out, total, nil
}

func (s *Service) requireOwnedActive(ctx context.Context, userID, itemID uuid.UUID) (*model.InventoryItem, error) {
	item, err := s.store.FindByID(ctx, itemID)
	if err != nil {
		return nil, err
	}
	if item == nil || item.UserID != userID || !item.IsActive {
		return nil, apperr.NotFound("Part not found")
	}
	return item, nil
}

func toResponse(item model.InventoryItem, dup *bool) ItemResponse {
	images := item.Images
	if images == nil {
		images = []string{}
	}
	return ItemResponse{
		ID: item.ID, PartName: item.PartName, LocalName: item.LocalName,
		Specification: item.Specification, Description: item.Description,
		VehicleCategory: item.VehicleCategory, Brand: item.Brand, Model: item.Model,
		Quantity: item.Quantity, MinQuantity: item.MinQuantity, SellingPrice: item.SellingPrice,
		Images: images, StockStatus: item.StockStatus(), IsActive: item.IsActive,
		IsDuplicate: dup, CreatedAt: item.CreatedAt, UpdatedAt: item.UpdatedAt,
	}
}

func applyStockFilter(item model.InventoryItem, filter string) bool {
	if filter == "" {
		return true
	}
	switch strings.ToUpper(filter) {
	case "LOW_STOCK":
		return item.Quantity <= item.MinQuantity
	case "OUT_OF_STOCK":
		return item.Quantity == 0
	case "IN_STOCK":
		return item.Quantity > 0
	default:
		return true
	}
}
