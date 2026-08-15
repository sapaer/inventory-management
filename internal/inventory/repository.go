package inventory

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

func (r *Repository) ListActiveByUser(ctx context.Context, userID uuid.UUID) ([]model.InventoryItem, error) {
	rows, err := r.db.Query(ctx, itemSelect+`
		WHERE user_id = $1 AND is_active = true
		ORDER BY updated_at DESC
	`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanItems(rows)
}

func (r *Repository) FullTextSearch(ctx context.Context, userID uuid.UUID, query string) ([]model.InventoryItem, error) {
	rows, err := r.db.Query(ctx, `
		SELECT `+itemColumns+`
		FROM inventory_items
		WHERE user_id = $1
		  AND search_vector @@ plainto_tsquery('english', $2)
		  AND is_active = true
		ORDER BY ts_rank(search_vector, plainto_tsquery('english', $2)) DESC
	`, userID, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanItems(rows)
}

func (r *Repository) FindByID(ctx context.Context, id uuid.UUID) (*model.InventoryItem, error) {
	item, err := scanItem(r.db.QueryRow(ctx, itemSelect+" WHERE id = $1", id))
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	return item, err
}

func (r *Repository) ExistsByPartName(ctx context.Context, userID uuid.UUID, partName string) (bool, error) {
	var exists bool
	err := r.db.QueryRow(ctx, `
		SELECT EXISTS(
			SELECT 1 FROM inventory_items
			WHERE user_id = $1 AND LOWER(part_name) = LOWER($2) AND is_active = true
		)
	`, userID, partName).Scan(&exists)
	return exists, err
}

func (r *Repository) Create(ctx context.Context, item *model.InventoryItem) (*model.InventoryItem, error) {
	images, _ := json.Marshal(item.Images)
	if item.Images == nil {
		images = []byte("[]")
	}
	row := r.db.QueryRow(ctx, `
		INSERT INTO inventory_items (
			user_id, part_name, local_name, specification, description, vehicle_category,
			brand, model, quantity, min_quantity, selling_price, cost_price, images, is_active
		) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,true)
		RETURNING `+itemColumns, item.UserID, item.PartName, item.LocalName, item.Specification,
		item.Description, item.VehicleCategory, item.Brand, item.Model, item.Quantity,
		item.MinQuantity, item.SellingPrice, item.CostPrice, images)
	return scanItem(row)
}

func (r *Repository) Update(ctx context.Context, item *model.InventoryItem) (*model.InventoryItem, error) {
	images, _ := json.Marshal(item.Images)
	if item.Images == nil {
		images = []byte("[]")
	}
	row := r.db.QueryRow(ctx, `
		UPDATE inventory_items SET
			part_name=$2, local_name=$3, specification=$4, description=$5, vehicle_category=$6,
			brand=$7, model=$8, quantity=$9, min_quantity=$10, selling_price=$11, cost_price=$12,
			images=$13, is_active=$14
		WHERE id=$1
		RETURNING `+itemColumns,
		item.ID, item.PartName, item.LocalName, item.Specification, item.Description,
		item.VehicleCategory, item.Brand, item.Model, item.Quantity, item.MinQuantity,
		item.SellingPrice, item.CostPrice, images, item.IsActive)
	return scanItem(row)
}

func (r *Repository) FindLowStockByUser(ctx context.Context, userID uuid.UUID) ([]model.InventoryItem, error) {
	rows, err := r.db.Query(ctx, itemSelect+`
		WHERE user_id = $1 AND quantity <= min_quantity AND is_active = true
	`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanItems(rows)
}

func (r *Repository) FindAllLowStockActive(ctx context.Context) ([]model.InventoryItem, error) {
	rows, err := r.db.Query(ctx, itemSelect+`
		WHERE quantity <= min_quantity AND is_active = true AND quantity >= 0
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanItems(rows)
}

func (r *Repository) InsertHistory(ctx context.Context, h *model.InventoryHistory) error {
	_, err := r.db.Exec(ctx, `
		INSERT INTO inventory_history (item_id, user_id, change_type, qty_before, qty_change, qty_after, note)
		VALUES ($1,$2,$3,$4,$5,$6,$7)
	`, h.ItemID, h.UserID, h.ChangeType, h.QtyBefore, h.QtyChange, h.QtyAfter, h.Note)
	return err
}

func (r *Repository) ListHistory(ctx context.Context, itemID uuid.UUID, limit, offset int) ([]model.InventoryHistory, int64, error) {
	var total int64
	if err := r.db.QueryRow(ctx, `SELECT COUNT(*) FROM inventory_history WHERE item_id = $1`, itemID).Scan(&total); err != nil {
		return nil, 0, err
	}
	rows, err := r.db.Query(ctx, `
		SELECT id, item_id, user_id, change_type, qty_before, qty_change, qty_after, note, created_at
		FROM inventory_history
		WHERE item_id = $1
		ORDER BY created_at DESC
		LIMIT $2 OFFSET $3
	`, itemID, limit, offset)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var out []model.InventoryHistory
	for rows.Next() {
		var h model.InventoryHistory
		if err := rows.Scan(&h.ID, &h.ItemID, &h.UserID, &h.ChangeType, &h.QtyBefore, &h.QtyChange, &h.QtyAfter, &h.Note, &h.CreatedAt); err != nil {
			return nil, 0, err
		}
		out = append(out, h)
	}
	return out, total, rows.Err()
}

const itemColumns = `id, user_id, part_name, local_name, specification, description, vehicle_category,
	brand, model, quantity, min_quantity, selling_price, cost_price, images, is_active, created_at, updated_at`

const itemSelect = `SELECT ` + itemColumns + ` FROM inventory_items`

func scanItem(row pgx.Row) (*model.InventoryItem, error) {
	var item model.InventoryItem
	var images []byte
	err := row.Scan(&item.ID, &item.UserID, &item.PartName, &item.LocalName, &item.Specification,
		&item.Description, &item.VehicleCategory, &item.Brand, &item.Model, &item.Quantity,
		&item.MinQuantity, &item.SellingPrice, &item.CostPrice, &images, &item.IsActive,
		&item.CreatedAt, &item.UpdatedAt)
	if err != nil {
		return nil, err
	}
	if len(images) > 0 {
		_ = json.Unmarshal(images, &item.Images)
	}
	if item.Images == nil {
		item.Images = []string{}
	}
	return &item, nil
}

func scanItems(rows pgx.Rows) ([]model.InventoryItem, error) {
	var items []model.InventoryItem
	for rows.Next() {
		item, err := scanItem(rows)
		if err != nil {
			return nil, err
		}
		items = append(items, *item)
	}
	if items == nil {
		items = []model.InventoryItem{}
	}
	return items, rows.Err()
}
