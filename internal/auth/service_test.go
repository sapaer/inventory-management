package auth

import (
	"context"
	"sync"
	"testing"
	"time"

	"github.com/google/uuid"

	"github.com/autoparts/inventory-management/internal/apperr"
	"github.com/autoparts/inventory-management/internal/jwtutil"
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
func (m *memCache) Incr(_ context.Context, key string) (int64, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	// naive
	m.data[key] = "1"
	return 1, nil
}
func (m *memCache) Expire(context.Context, string, time.Duration) error { return nil }
func (m *memCache) Del(_ context.Context, keys ...string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	for _, k := range keys {
		delete(m.data, k)
	}
	return nil
}
func (m *memCache) Exists(_ context.Context, key string) (bool, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	_, ok := m.data[key]
	return ok, nil
}

type fakeUsers struct {
	byPhone map[string]*model.User
	byID    map[uuid.UUID]*model.User
}

func newFakeUsers() *fakeUsers {
	return &fakeUsers{byPhone: map[string]*model.User{}, byID: map[uuid.UUID]*model.User{}}
}
func (f *fakeUsers) ExistsByPhone(_ context.Context, phone string) (bool, error) {
	_, ok := f.byPhone[phone]
	return ok, nil
}
func (f *fakeUsers) FindByPhone(_ context.Context, phone string) (*model.User, error) {
	return f.byPhone[phone], nil
}
func (f *fakeUsers) FindByID(_ context.Context, id uuid.UUID) (*model.User, error) {
	return f.byID[id], nil
}
func (f *fakeUsers) Create(_ context.Context, phone string) (*model.User, error) {
	u := &model.User{ID: uuid.New(), Phone: phone, IsVerified: true, OnboardingStatus: model.OnboardingRegistered}
	f.byPhone[phone] = u
	f.byID[u.ID] = u
	return u, nil
}
func (f *fakeUsers) Update(_ context.Context, user *model.User) (*model.User, error) {
	f.byID[user.ID] = user
	f.byPhone[user.Phone] = user
	return user, nil
}
func (f *fakeUsers) FindPrimaryLocation(context.Context, uuid.UUID) (*model.UserLocation, error) {
	return nil, nil
}
func (f *fakeUsers) UpsertLocation(context.Context, *model.UserLocation) error { return nil }
func (f *fakeUsers) ReplaceVehicleCategories(context.Context, uuid.UUID, []model.VehicleCategory) error {
	return nil
}

type fakeOTP struct{ last string }

func (f *fakeOTP) SendOTP(_ context.Context, _, otp string) error { f.last = otp; return nil }

func newTestService(cache *memCache, users *fakeUsers, otp *fakeOTP) *Service {
	jwtSvc, err := jwtutil.New("QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=", 24)
	if err != nil {
		panic(err)
	}
	return NewService(users, cache, jwtSvc, otp)
}

func TestRequestOTPRejectsMaxAttempts(t *testing.T) {
	cache := newMemCache()
	cache.data["otp_attempts:9876543210"] = "5"
	svc := newTestService(cache, newFakeUsers(), &fakeOTP{})
	err := svc.RequestOTP(context.Background(), "9876543210")
	ae, ok := apperr.AsAppError(err)
	if !ok || ae.Code != "OTP_MAX_ATTEMPTS" {
		t.Fatalf("expected OTP_MAX_ATTEMPTS, got %v", err)
	}
}

func TestRequestOTPStoresAndSends(t *testing.T) {
	cache := newMemCache()
	otp := &fakeOTP{}
	svc := newTestService(cache, newFakeUsers(), otp)
	if err := svc.RequestOTP(context.Background(), "9876543210"); err != nil {
		t.Fatal(err)
	}
	if cache.data["otp:9876543210"] == "" {
		t.Fatal("otp not stored")
	}
	if otp.last == "" {
		t.Fatal("otp not sent")
	}
}

func TestVerifyOTPExpired(t *testing.T) {
	svc := newTestService(newMemCache(), newFakeUsers(), &fakeOTP{})
	_, err := svc.VerifyOTP(context.Background(), "9876543210", "123456")
	ae, _ := apperr.AsAppError(err)
	if ae == nil || ae.Code != "OTP_EXPIRED" {
		t.Fatalf("expected OTP_EXPIRED, got %v", err)
	}
}

func TestVerifyOTPInvalid(t *testing.T) {
	cache := newMemCache()
	cache.data["otp:9876543210"] = "111111"
	svc := newTestService(cache, newFakeUsers(), &fakeOTP{})
	_, err := svc.VerifyOTP(context.Background(), "9876543210", "123456")
	ae, _ := apperr.AsAppError(err)
	if ae == nil || ae.Code != "OTP_INVALID" {
		t.Fatalf("expected OTP_INVALID, got %v", err)
	}
}

func TestVerifyOTPCreatesUser(t *testing.T) {
	cache := newMemCache()
	cache.data["otp:9876543210"] = "123456"
	svc := newTestService(cache, newFakeUsers(), &fakeOTP{})
	resp, err := svc.VerifyOTP(context.Background(), "9876543210", "123456")
	if err != nil {
		t.Fatal(err)
	}
	if !resp.IsNewUser {
		t.Fatal("expected new user")
	}
	if resp.AccessToken == "" || resp.RefreshToken == "" {
		t.Fatal("expected tokens")
	}
}
