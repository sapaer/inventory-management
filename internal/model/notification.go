package model

import (
	"time"

	"github.com/google/uuid"
)

type Notification struct {
	ID        uuid.UUID
	UserID    uuid.UUID
	Type      NotificationType
	Title     *string
	Body      *string
	Data      map[string]any
	Channel   *NotificationChannel
	IsRead    bool
	SentAt    *time.Time
	CreatedAt time.Time
}
