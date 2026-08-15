package notification

import (
	"context"
	"encoding/json"
	"errors"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/autoparts/inventory-management/internal/model"
)

type Repository struct {
	db *pgxpool.Pool
}

func NewRepository(db *pgxpool.Pool) *Repository {
	return &Repository{db: db}
}

func (r *Repository) Create(ctx context.Context, n *model.Notification) (*model.Notification, error) {
	data, _ := json.Marshal(n.Data)
	row := r.db.QueryRow(ctx, `
		INSERT INTO notifications (user_id, type, title, body, data, channel, is_read, sent_at)
		VALUES ($1,$2,$3,$4,$5,$6,false,$7)
		RETURNING id, user_id, type, title, body, data, channel, is_read, sent_at, created_at
	`, n.UserID, n.Type, n.Title, n.Body, data, n.Channel, n.SentAt)
	return scanNotification(row)
}

func (r *Repository) ListByUser(ctx context.Context, userID uuid.UUID, limit, offset int) ([]model.Notification, int64, error) {
	var total int64
	if err := r.db.QueryRow(ctx, `SELECT COUNT(*) FROM notifications WHERE user_id = $1`, userID).Scan(&total); err != nil {
		return nil, 0, err
	}
	rows, err := r.db.Query(ctx, `
		SELECT id, user_id, type, title, body, data, channel, is_read, sent_at, created_at
		FROM notifications
		WHERE user_id = $1
		ORDER BY created_at DESC
		LIMIT $2 OFFSET $3
	`, userID, limit, offset)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()
	var out []model.Notification
	for rows.Next() {
		n, err := scanNotification(rows)
		if err != nil {
			return nil, 0, err
		}
		out = append(out, *n)
	}
	if out == nil {
		out = []model.Notification{}
	}
	return out, total, rows.Err()
}

func (r *Repository) FindByID(ctx context.Context, id uuid.UUID) (*model.Notification, error) {
	n, err := scanNotification(r.db.QueryRow(ctx, `
		SELECT id, user_id, type, title, body, data, channel, is_read, sent_at, created_at
		FROM notifications WHERE id = $1
	`, id))
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	return n, err
}

func (r *Repository) MarkRead(ctx context.Context, id uuid.UUID) (*model.Notification, error) {
	return scanNotification(r.db.QueryRow(ctx, `
		UPDATE notifications SET is_read = true WHERE id = $1
		RETURNING id, user_id, type, title, body, data, channel, is_read, sent_at, created_at
	`, id))
}

func scanNotification(row pgx.Row) (*model.Notification, error) {
	var n model.Notification
	var data []byte
	if err := row.Scan(&n.ID, &n.UserID, &n.Type, &n.Title, &n.Body, &data, &n.Channel, &n.IsRead, &n.SentAt, &n.CreatedAt); err != nil {
		return nil, err
	}
	if len(data) > 0 {
		_ = json.Unmarshal(data, &n.Data)
	}
	return &n, nil
}
