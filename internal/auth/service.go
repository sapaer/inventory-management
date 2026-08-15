package auth

import (
	"context"
	"crypto/rand"
	"fmt"
	"log/slog"
	"math/big"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"

	"github.com/autoparts/inventory-management/internal/apperr"
	"github.com/autoparts/inventory-management/internal/jwtutil"
	"github.com/autoparts/inventory-management/internal/model"
	"github.com/autoparts/inventory-management/internal/store"
)

const (
	otpExpirySeconds      = 300
	maxOTPAttempts        = 5
	attemptWindowSeconds  = 600
	refreshTokenTTLDays   = 30
)

type UserStore interface {
	ExistsByPhone(ctx context.Context, phone string) (bool, error)
	FindByPhone(ctx context.Context, phone string) (*model.User, error)
	FindByID(ctx context.Context, id uuid.UUID) (*model.User, error)
	Create(ctx context.Context, phone string) (*model.User, error)
	Update(ctx context.Context, user *model.User) (*model.User, error)
	FindPrimaryLocation(ctx context.Context, userID uuid.UUID) (*model.UserLocation, error)
	UpsertLocation(ctx context.Context, loc *model.UserLocation) error
	ReplaceVehicleCategories(ctx context.Context, userID uuid.UUID, categories []model.VehicleCategory) error
}

type OTPSender interface {
	SendOTP(ctx context.Context, phone, otp string) error
}

type Service struct {
	users UserStore
	cache store.Cache
	jwt   *jwtutil.Service
	otp   OTPSender
}

func NewService(users UserStore, cache store.Cache, jwtSvc *jwtutil.Service, otp OTPSender) *Service {
	return &Service{users: users, cache: cache, jwt: jwtSvc, otp: otp}
}

type UserDTO struct {
	ID               uuid.UUID             `json:"id"`
	Phone            string                `json:"phone"`
	Name             *string               `json:"name"`
	ShopName         *string               `json:"shopName"`
	OnboardingStatus model.OnboardingStatus `json:"onboardingStatus"`
}

type AuthResponse struct {
	AccessToken  string  `json:"accessToken"`
	RefreshToken string  `json:"refreshToken"`
	User         UserDTO `json:"user"`
	IsNewUser    bool    `json:"isNewUser"`
}

type ProfileUpdate struct {
	Name               *string                 `json:"name"`
	ShopName           *string                 `json:"shopName"`
	Email              *string                 `json:"email"`
	BusinessType       *model.BusinessType     `json:"businessType"`
	Address            *string                 `json:"address"`
	Area               *string                 `json:"area"`
	City               *string                 `json:"city"`
	State              *string                 `json:"state"`
	Pincode            *string                 `json:"pincode"`
	GeoLat             *float64                `json:"geoLat"`
	GeoLng             *float64                `json:"geoLng"`
	VehicleCategories  []model.VehicleCategory `json:"vehicleCategories"`
}

func (s *Service) RequestOTP(ctx context.Context, phone string) error {
	attemptsKey := "otp_attempts:" + phone
	attempts, err := s.cache.Get(ctx, attemptsKey)
	if err != nil {
		return err
	}
	if attempts != "" {
		n, _ := strconv.Atoi(attempts)
		if n >= maxOTPAttempts {
			return apperr.TooManyRequests("OTP_MAX_ATTEMPTS", "Too many OTP requests. Try again in 10 minutes.")
		}
	}

	otp, err := generateOTP()
	if err != nil {
		return err
	}
	if err := s.cache.Set(ctx, "otp:"+phone, otp, time.Duration(otpExpirySeconds)*time.Second); err != nil {
		return err
	}
	if _, err := s.cache.Incr(ctx, attemptsKey); err != nil {
		return err
	}
	if err := s.cache.Expire(ctx, attemptsKey, time.Duration(attemptWindowSeconds)*time.Second); err != nil {
		return err
	}
	if err := s.otp.SendOTP(ctx, phone, otp); err != nil {
		slog.Error("whatsapp otp send failed", "err", err, "phone", phone)
	}
	slog.Info("OTP sent", "phone", phone)
	return nil
}

func (s *Service) VerifyOTP(ctx context.Context, phone, submitted string) (*AuthResponse, error) {
	stored, err := s.cache.Get(ctx, "otp:"+phone)
	if err != nil {
		return nil, err
	}
	if stored == "" {
		return nil, apperr.BadRequest("OTP_EXPIRED", "OTP has expired. Please request a new one.")
	}
	if stored != submitted {
		return nil, apperr.BadRequest("OTP_INVALID", "Incorrect OTP. Please try again.")
	}

	_ = s.cache.Del(ctx, "otp:"+phone, "otp_attempts:"+phone)

	isNew, err := s.users.ExistsByPhone(ctx, phone)
	if err != nil {
		return nil, err
	}
	isNewUser := !isNew

	user, err := s.users.FindByPhone(ctx, phone)
	if err != nil {
		return nil, err
	}
	if user == nil {
		user, err = s.users.Create(ctx, phone)
		if err != nil {
			return nil, err
		}
	} else if !user.IsVerified {
		user.IsVerified = true
		user, err = s.users.Update(ctx, user)
		if err != nil {
			return nil, err
		}
	}

	access, err := s.jwt.GenerateAccessToken(user.ID, user.Phone)
	if err != nil {
		return nil, err
	}
	refresh := user.ID.String() + ":" + uuid.NewString()
	if err := s.cache.Set(ctx, "session:"+user.ID.String(), refresh, time.Duration(refreshTokenTTLDays)*24*time.Hour); err != nil {
		return nil, err
	}

	return &AuthResponse{
		AccessToken:  access,
		RefreshToken: refresh,
		IsNewUser:    isNewUser,
		User:         toUserDTO(user),
	}, nil
}

func (s *Service) RefreshToken(ctx context.Context, refreshToken string) (map[string]string, error) {
	if strings.TrimSpace(refreshToken) == "" {
		return nil, apperr.Unauthorized("Invalid refresh token")
	}
	parts := strings.SplitN(refreshToken, ":", 2)
	if len(parts) != 2 {
		return nil, apperr.Unauthorized("Invalid refresh token")
	}
	userID, err := uuid.Parse(parts[0])
	if err != nil {
		return nil, apperr.Unauthorized("Invalid refresh token")
	}

	sessionKey := "session:" + userID.String()
	stored, err := s.cache.Get(ctx, sessionKey)
	if err != nil {
		return nil, err
	}
	if stored == "" || stored != refreshToken {
		return nil, apperr.Unauthorized("Refresh token expired or invalid")
	}

	user, err := s.users.FindByID(ctx, userID)
	if err != nil {
		return nil, err
	}
	if user == nil {
		return nil, apperr.Unauthorized("User not found")
	}

	newRefresh := userID.String() + ":" + uuid.NewString()
	if err := s.cache.Set(ctx, sessionKey, newRefresh, time.Duration(refreshTokenTTLDays)*24*time.Hour); err != nil {
		return nil, err
	}
	access, err := s.jwt.GenerateAccessToken(userID, user.Phone)
	if err != nil {
		return nil, err
	}
	return map[string]string{"accessToken": access, "refreshToken": newRefresh}, nil
}

func (s *Service) Logout(ctx context.Context, userID uuid.UUID) error {
	return s.cache.Del(ctx, "session:"+userID.String())
}

func (s *Service) GetProfile(ctx context.Context, userID uuid.UUID) (*UserDTO, error) {
	user, err := s.users.FindByID(ctx, userID)
	if err != nil {
		return nil, err
	}
	if user == nil {
		return nil, apperr.NotFound("User not found")
	}
	dto := toUserDTO(user)
	return &dto, nil
}

func (s *Service) UpdateProfile(ctx context.Context, userID uuid.UUID, dto ProfileUpdate) (*UserDTO, error) {
	user, err := s.users.FindByID(ctx, userID)
	if err != nil {
		return nil, err
	}
	if user == nil {
		return nil, apperr.NotFound("User not found")
	}
	if dto.Name != nil {
		user.Name = dto.Name
	}
	if dto.ShopName != nil {
		user.ShopName = dto.ShopName
	}
	if dto.Email != nil {
		user.Email = dto.Email
	}
	if dto.BusinessType != nil {
		user.BusinessType = dto.BusinessType
	}

	if err := s.upsertLocation(ctx, userID, dto); err != nil {
		return nil, err
	}
	if dto.VehicleCategories != nil {
		if err := s.users.ReplaceVehicleCategories(ctx, userID, dto.VehicleCategories); err != nil {
			return nil, err
		}
	}
	if user.OnboardingStatus == model.OnboardingRegistered && user.Name != nil && user.ShopName != nil {
		user.OnboardingStatus = model.OnboardingProfiled
	}

	user, err = s.users.Update(ctx, user)
	if err != nil {
		return nil, err
	}
	out := toUserDTO(user)
	return &out, nil
}

func (s *Service) upsertLocation(ctx context.Context, userID uuid.UUID, dto ProfileUpdate) error {
	if dto.Address == nil && dto.Area == nil && dto.City == nil && dto.Pincode == nil && dto.GeoLat == nil && dto.GeoLng == nil {
		return nil
	}
	loc, err := s.users.FindPrimaryLocation(ctx, userID)
	if err != nil {
		return err
	}
	if loc == nil {
		loc = &model.UserLocation{UserID: userID, IsPrimary: true}
	}
	if dto.Address != nil {
		loc.Address = dto.Address
	}
	if dto.Area != nil {
		loc.Area = dto.Area
	}
	if dto.City != nil {
		loc.City = dto.City
	}
	if dto.State != nil {
		loc.State = dto.State
	}
	if dto.Pincode != nil {
		loc.Pincode = dto.Pincode
	}
	if dto.GeoLat != nil {
		loc.GeoLat = dto.GeoLat
	}
	if dto.GeoLng != nil {
		loc.GeoLng = dto.GeoLng
	}
	return s.users.UpsertLocation(ctx, loc)
}

func toUserDTO(user *model.User) UserDTO {
	return UserDTO{
		ID:               user.ID,
		Phone:            user.Phone,
		Name:             user.Name,
		ShopName:         user.ShopName,
		OnboardingStatus: user.OnboardingStatus,
	}
}

func generateOTP() (string, error) {
	n, err := rand.Int(rand.Reader, big.NewInt(1_000_000))
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("%06d", n.Int64()), nil
}
