package inventory

import (
	"context"
	"testing"

	"github.com/google/uuid"

	"github.com/autoparts/inventory-management/internal/apperr"
	"github.com/autoparts/inventory-management/internal/model"
)

type fakeStore struct {
	items    map[uuid.UUID]*model.InventoryItem
	history  int
	exists   bool
}

func newFakeStore() *fakeStore { return &fakeStore{items: map[uuid.UUID]*model.InventoryItem{}} }

func (f *fakeStore) ListActiveByUser(context.Context, uuid.UUID) ([]model.InventoryItem, error) {
	return nil, nil
}
func (f *fakeStore) FullTextSearch(context.Context, uuid.UUID, string) ([]model.InventoryItem, error) {
	return nil, nil
}
func (f *fakeStore) FindByID(_ context.Context, id uuid.UUID) (*model.InventoryItem, error) {
	return f.items[id], nil
}
func (f *fakeStore) ExistsByPartName(context.Context, uuid.UUID, string) (bool, error) {
	return f.exists, nil
}
func (f *fakeStore) Create(_ context.Context, item *model.InventoryItem) (*model.InventoryItem, error) {
	item.ID = uuid.New()
	item.IsActive = true
	cp := *item
	f.items[item.ID] = &cp
	return &cp, nil
}
func (f *fakeStore) Update(_ context.Context, item *model.InventoryItem) (*model.InventoryItem, error) {
	cp := *item
	f.items[item.ID] = &cp
	return &cp, nil
}
func (f *fakeStore) FindLowStockByUser(context.Context, uuid.UUID) ([]model.InventoryItem, error) {
	return nil, nil
}
func (f *fakeStore) FindAllLowStockActive(context.Context) ([]model.InventoryItem, error) {
	return nil, nil
}
func (f *fakeStore) InsertHistory(context.Context, *model.InventoryHistory) error {
	f.history++
	return nil
}
func (f *fakeStore) ListHistory(context.Context, uuid.UUID, int, int) ([]model.InventoryHistory, int64, error) {
	return nil, 0, nil
}

type fakeNotifier struct{ called bool }

func (f *fakeNotifier) TriggerLowStockCheck(context.Context, model.InventoryItem) error {
	f.called = true
	return nil
}

func TestAddPartSavesAndLogsHistory(t *testing.T) {
	store := newFakeStore()
	svc := NewService(store, &fakeNotifier{})
	qty := 10
	min := 2
	price := 499.0
	resp, err := svc.Add(context.Background(), uuid.New(), AddPartRequest{
		PartName:        "Brake Pad",
		VehicleCategory: model.VehicleTwoWheeler,
		Quantity:        &qty,
		MinQuantity:     &min,
		SellingPrice:    &price,
	})
	if err != nil {
		t.Fatal(err)
	}
	if resp.PartName != "Brake Pad" || resp.Quantity != 10 || resp.StockStatus != "IN_STOCK" {
		t.Fatalf("unexpected response: %+v", resp)
	}
	if resp.IsDuplicate == nil || *resp.IsDuplicate {
		t.Fatal("expected isDuplicate false")
	}
	if store.history != 1 {
		t.Fatalf("expected history log, got %d", store.history)
	}
}

func TestUpdateQuantityRejectsNegativeStock(t *testing.T) {
	userID := uuid.New()
	itemID := uuid.New()
	store := newFakeStore()
	store.items[itemID] = &model.InventoryItem{ID: itemID, UserID: userID, Quantity: 2, MinQuantity: 2, IsActive: true}
	svc := NewService(store, &fakeNotifier{})
	_, err := svc.UpdateQuantity(context.Background(), userID, itemID, QuantityUpdateRequest{Change: -5, ChangeType: model.ChangeSold})
	ae, _ := apperr.AsAppError(err)
	if ae == nil || ae.Code != "INSUFFICIENT_STOCK" {
		t.Fatalf("expected INSUFFICIENT_STOCK, got %v", err)
	}
}

func TestUpdateQuantityTriggersLowStock(t *testing.T) {
	userID := uuid.New()
	itemID := uuid.New()
	store := newFakeStore()
	store.items[itemID] = &model.InventoryItem{
		ID: itemID, UserID: userID, PartName: "Oil Filter", Quantity: 5, MinQuantity: 2, IsActive: true,
	}
	n := &fakeNotifier{}
	svc := NewService(store, n)
	resp, err := svc.UpdateQuantity(context.Background(), userID, itemID, QuantityUpdateRequest{Change: -3, ChangeType: model.ChangeSold})
	if err != nil {
		t.Fatal(err)
	}
	if resp.Quantity != 2 || resp.StockStatus != "LOW_STOCK" {
		t.Fatalf("unexpected response: %+v", resp)
	}
	if !n.called {
		t.Fatal("expected low stock notification")
	}
}
