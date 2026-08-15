package notification

import (
	"context"
	"sync"
	"testing"
	"time"

	"github.com/google/uuid"

	"github.com/autoparts/inventory-management/internal/model"
)

type memCache struct {
	mu   sync.Mutex
	data map[string]string
}

func newMemCache() *memCache { return &memCache{data: map[string]string{}} }

func (m *memCache) Get(_ context.Context, key string) (string, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.data[key], nil
}
func (m *memCache) Set(_ context.Context, key, value string, _ time.Duration) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.data[key] = value
	return nil
}
func (m *memCache) Incr(context.Context, string) (int64, error)              { return 0, nil }
func (m *memCache) Expire(context.Context, string, time.Duration) error      { return nil }
func (m *memCache) Del(context.Context, ...string) error                     { return nil }
func (m *memCache) Exists(_ context.Context, key string) (bool, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	_, ok := m.data[key]
	return ok, nil
}

type fakeFinder struct{ items []model.InventoryItem }

func (f *fakeFinder) FindAllLowStockActive(context.Context) ([]model.InventoryItem, error) {
	return f.items, nil
}

type fakeAlerts struct{ n int }

func (f *fakeAlerts) SendLowStockAlert(context.Context, model.InventoryItem) error {
	f.n++
	return nil
}

func TestCheckLowStockSendsWhenNoCooldown(t *testing.T) {
	item := model.InventoryItem{ID: uuid.New(), PartName: "Spark Plug", Quantity: 1, MinQuantity: 2, IsActive: true}
	alerts := &fakeAlerts{}
	s := NewScheduler(&fakeFinder{items: []model.InventoryItem{item}}, alerts, newMemCache())
	s.CheckLowStockItems(context.Background())
	if alerts.n != 1 {
		t.Fatalf("expected 1 alert, got %d", alerts.n)
	}
}

func TestCheckLowStockSkipsCooldown(t *testing.T) {
	item := model.InventoryItem{ID: uuid.New(), PartName: "Spark Plug", Quantity: 1, MinQuantity: 2, IsActive: true}
	cache := newMemCache()
	cache.data["low_stock_notified:"+item.ID.String()] = "1"
	alerts := &fakeAlerts{}
	s := NewScheduler(&fakeFinder{items: []model.InventoryItem{item}}, alerts, cache)
	s.CheckLowStockItems(context.Background())
	if alerts.n != 0 {
		t.Fatalf("expected 0 alerts, got %d", alerts.n)
	}
}
