package model

import (
	"time"

	"github.com/google/uuid"
)

type InventoryItem struct {
	ID              uuid.UUID
	UserID          uuid.UUID
	PartName        string
	LocalName       *string
	Specification   *string
	Description     *string
	VehicleCategory *VehicleCategory
	Brand           *string
	Model           *string
	Quantity        int
	MinQuantity     int
	SellingPrice    *float64
	CostPrice       *float64
	Images          []string
	IsActive        bool
	CreatedAt       time.Time
	UpdatedAt       time.Time
}

func (i InventoryItem) StockStatus() string {
	if i.Quantity == 0 {
		return "OUT_OF_STOCK"
	}
	if i.Quantity <= i.MinQuantity {
		return "LOW_STOCK"
	}
	return "IN_STOCK"
}

type InventoryHistory struct {
	ID         uuid.UUID
	ItemID     uuid.UUID
	UserID     uuid.UUID
	ChangeType ChangeType
	QtyBefore  int
	QtyChange  int
	QtyAfter   int
	Note       *string
	CreatedAt  time.Time
}
