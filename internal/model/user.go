package model

import (
	"time"

	"github.com/google/uuid"
)

type User struct {
	ID               uuid.UUID
	Phone            string
	Name             *string
	ShopName         *string
	Email            *string
	BusinessType     *BusinessType
	OnboardingStatus OnboardingStatus
	IsVerified       bool
	CreatedAt        time.Time
	UpdatedAt        time.Time
}

type UserLocation struct {
	ID        uuid.UUID
	UserID    uuid.UUID
	Address   *string
	Area      *string
	City      *string
	State     *string
	Pincode   *string
	GeoLat    *float64
	GeoLng    *float64
	IsPrimary bool
	CreatedAt time.Time
}
