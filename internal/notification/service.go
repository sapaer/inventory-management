package notification

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"

	"github.com/autoparts/inventory-management/internal/apperr"
	"github.com/autoparts/inventory-management/internal/model"
)

type Store interface {
	Create(ctx context.Context, n *model.Notification) (*model.Notification, error)
	ListByUser(ctx context.Context, userID uuid.UUID, limit, offset int) ([]model.Notification, int64, error)
	FindByID(ctx context.Context, id uuid.UUID) (*model.Notification, error)
	MarkRead(ctx context.Context, id uuid.UUID) (*model.Notification, error)
}

type UserLookup interface {
	FindByID(ctx context.Context, id uuid.UUID) (*model.User, error)
}

type WhatsAppSender interface {
	SendLowStockAlert(ctx context.Context, phone, partName string, qty int) error
}

type PushSender interface {
	SendLowStockPush(ctx context.Context, userID, partName string, qty int) error
}

type Service struct {
	store Store
	users UserLookup
	wa    WhatsAppSender
	push  PushSender
}

func NewService(store Store, users UserLookup, wa WhatsAppSender, push PushSender) *Service {
	return &Service{store: store, users: users, wa: wa, push: push}
}

type DTO struct {
	ID        uuid.UUID                 `json:"id"`
	Type      model.NotificationType    `json:"type"`
	Title     *string                   `json:"title"`
	Body      *string                   `json:"body"`
	Data      map[string]any            `json:"data"`
	Channel   *model.NotificationChannel `json:"channel"`
	IsRead    bool                      `json:"isRead"`
	SentAt    *time.Time                `json:"sentAt"`
	CreatedAt time.Time                 `json:"createdAt"`
}

func (s *Service) SendLowStockAlert(ctx context.Context, item model.InventoryItem) error {
	user, err := s.users.FindByID(ctx, item.UserID)
	if err != nil || user == nil {
		return err
	}
	if s.wa != nil {
		_ = s.wa.SendLowStockAlert(ctx, user.Phone, item.PartName, item.Quantity)
	}
	if s.push != nil {
		_ = s.push.SendLowStockPush(ctx, user.ID.String(), item.PartName, item.Quantity)
	}
	title := "Low stock alert"
	body := fmt.Sprintf("%s — only %d units left", item.PartName, item.Quantity)
	channel := model.ChannelWhatsApp
	now := time.Now()
	_, err = s.store.Create(ctx, &model.Notification{
		UserID:  user.ID,
		Type:    model.NotifyLowStock,
		Title:   &title,
		Body:    &body,
		Channel: &channel,
		Data: map[string]any{
			"item_id":       item.ID.String(),
			"qty_remaining": item.Quantity,
		},
		SentAt: &now,
	})
	return err
}

func (s *Service) TriggerLowStockCheck(ctx context.Context, item model.InventoryItem) error {
	return s.SendLowStockAlert(ctx, item)
}

func (s *Service) List(ctx context.Context, userID uuid.UUID, page, limit int) ([]DTO, int64, error) {
	if page < 1 {
		page = 1
	}
	if limit <= 0 {
		limit = 20
	}
	rows, total, err := s.store.ListByUser(ctx, userID, limit, (page-1)*limit)
	if err != nil {
		return nil, 0, err
	}
	out := make([]DTO, 0, len(rows))
	for _, n := range rows {
		out = append(out, toDTO(n))
	}
	return out, total, nil
}

func (s *Service) MarkRead(ctx context.Context, userID, id uuid.UUID) (*DTO, error) {
	n, err := s.store.FindByID(ctx, id)
	if err != nil {
		return nil, err
	}
	if n == nil || n.UserID != userID {
		return nil, apperr.NotFound("Notification not found")
	}
	saved, err := s.store.MarkRead(ctx, id)
	if err != nil {
		return nil, err
	}
	dto := toDTO(*saved)
	return &dto, nil
}

func toDTO(n model.Notification) DTO {
	return DTO{
		ID: n.ID, Type: n.Type, Title: n.Title, Body: n.Body, Data: n.Data,
		Channel: n.Channel, IsRead: n.IsRead, SentAt: n.SentAt, CreatedAt: n.CreatedAt,
	}
}
