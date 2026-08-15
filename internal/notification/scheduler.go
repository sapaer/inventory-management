package notification

import (
	"context"
	"log/slog"
	"time"

	"github.com/autoparts/inventory-management/internal/model"
	"github.com/autoparts/inventory-management/internal/store"
)

type LowStockFinder interface {
	FindAllLowStockActive(ctx context.Context) ([]model.InventoryItem, error)
}

type AlertSender interface {
	SendLowStockAlert(ctx context.Context, item model.InventoryItem) error
}

type Scheduler struct {
	finder LowStockFinder
	alerts AlertSender
	cache  store.Cache
}

func NewScheduler(finder LowStockFinder, alerts AlertSender, cache store.Cache) *Scheduler {
	return &Scheduler{finder: finder, alerts: alerts, cache: cache}
}

func (s *Scheduler) Start(ctx context.Context) {
	ticker := time.NewTicker(5 * time.Minute)
	go func() {
		defer ticker.Stop()
		s.CheckLowStockItems(ctx)
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				s.CheckLowStockItems(ctx)
			}
		}
	}()
}

func (s *Scheduler) CheckLowStockItems(ctx context.Context) {
	slog.Info("low stock cron started")
	checked, notified := 0, 0
	items, err := s.finder.FindAllLowStockActive(ctx)
	if err != nil {
		slog.Error("low stock cron failed", "err", err)
		return
	}
	for _, item := range items {
		checked++
		key := "low_stock_notified:" + item.ID.String()
		exists, err := s.cache.Exists(ctx, key)
		if err != nil {
			slog.Error("cooldown check failed", "err", err, "itemId", item.ID)
			continue
		}
		if exists {
			continue
		}
		if err := s.alerts.SendLowStockAlert(ctx, item); err != nil {
			slog.Error("low stock alert failed", "err", err, "itemId", item.ID)
			continue
		}
		_ = s.cache.Set(ctx, key, "1", 24*time.Hour)
		notified++
	}
	slog.Info("low stock cron done", "checked", checked, "notified", notified)
}
