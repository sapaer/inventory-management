package auth

import (
	"context"
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

func (r *Repository) ExistsByPhone(ctx context.Context, phone string) (bool, error) {
	var exists bool
	err := r.db.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM users WHERE phone = $1)`, phone).Scan(&exists)
	return exists, err
}

func (r *Repository) FindByPhone(ctx context.Context, phone string) (*model.User, error) {
	return r.scanUser(r.db.QueryRow(ctx, userSelect+" WHERE phone = $1", phone))
}

func (r *Repository) FindByID(ctx context.Context, id uuid.UUID) (*model.User, error) {
	return r.scanUser(r.db.QueryRow(ctx, userSelect+" WHERE id = $1", id))
}

func (r *Repository) Create(ctx context.Context, phone string) (*model.User, error) {
	row := r.db.QueryRow(ctx, `
		INSERT INTO users (phone, is_verified, onboarding_status)
		VALUES ($1, true, 'REGISTERED')
		RETURNING id, phone, name, shop_name, email, business_type, onboarding_status, is_verified, created_at, updated_at
	`, phone)
	return r.scanUser(row)
}

func (r *Repository) Update(ctx context.Context, user *model.User) (*model.User, error) {
	row := r.db.QueryRow(ctx, `
		UPDATE users
		SET name = $2, shop_name = $3, email = $4, business_type = $5,
		    onboarding_status = $6, is_verified = $7
		WHERE id = $1
		RETURNING id, phone, name, shop_name, email, business_type, onboarding_status, is_verified, created_at, updated_at
	`, user.ID, user.Name, user.ShopName, user.Email, user.BusinessType, user.OnboardingStatus, user.IsVerified)
	return r.scanUser(row)
}

func (r *Repository) FindPrimaryLocation(ctx context.Context, userID uuid.UUID) (*model.UserLocation, error) {
	row := r.db.QueryRow(ctx, `
		SELECT id, user_id, address, area, city, state, pincode, geo_lat, geo_lng, is_primary, created_at
		FROM user_locations
		WHERE user_id = $1 AND is_primary = true
	`, userID)
	loc, err := scanLocation(row)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	return loc, err
}

func (r *Repository) UpsertLocation(ctx context.Context, loc *model.UserLocation) error {
	if loc.ID == uuid.Nil {
		_, err := r.db.Exec(ctx, `
			INSERT INTO user_locations (user_id, address, area, city, state, pincode, geo_lat, geo_lng, is_primary)
			VALUES ($1,$2,$3,$4,$5,$6,$7,$8,true)
		`, loc.UserID, loc.Address, loc.Area, loc.City, loc.State, loc.Pincode, loc.GeoLat, loc.GeoLng)
		return err
	}
	_, err := r.db.Exec(ctx, `
		UPDATE user_locations
		SET address=$2, area=$3, city=$4, state=$5, pincode=$6, geo_lat=$7, geo_lng=$8
		WHERE id=$1
	`, loc.ID, loc.Address, loc.Area, loc.City, loc.State, loc.Pincode, loc.GeoLat, loc.GeoLng)
	return err
}

func (r *Repository) ReplaceVehicleCategories(ctx context.Context, userID uuid.UUID, categories []model.VehicleCategory) error {
	tx, err := r.db.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	if _, err := tx.Exec(ctx, `DELETE FROM user_vehicle_categories WHERE user_id = $1`, userID); err != nil {
		return err
	}
	for _, c := range categories {
		if _, err := tx.Exec(ctx, `INSERT INTO user_vehicle_categories (user_id, category) VALUES ($1, $2)`, userID, c); err != nil {
			return err
		}
	}
	return tx.Commit(ctx)
}

const userSelect = `
	SELECT id, phone, name, shop_name, email, business_type, onboarding_status, is_verified, created_at, updated_at
	FROM users`

func (r *Repository) scanUser(row pgx.Row) (*model.User, error) {
	var u model.User
	err := row.Scan(&u.ID, &u.Phone, &u.Name, &u.ShopName, &u.Email, &u.BusinessType,
		&u.OnboardingStatus, &u.IsVerified, &u.CreatedAt, &u.UpdatedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &u, nil
}

func scanLocation(row pgx.Row) (*model.UserLocation, error) {
	var loc model.UserLocation
	err := row.Scan(&loc.ID, &loc.UserID, &loc.Address, &loc.Area, &loc.City, &loc.State,
		&loc.Pincode, &loc.GeoLat, &loc.GeoLng, &loc.IsPrimary, &loc.CreatedAt)
	if err != nil {
		return nil, err
	}
	return &loc, nil
}
