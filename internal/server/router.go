package server

import (
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"

	"github.com/autoparts/inventory-management/internal/api"
	"github.com/autoparts/inventory-management/internal/auth"
	"github.com/autoparts/inventory-management/internal/inventory"
	"github.com/autoparts/inventory-management/internal/jwtutil"
	"github.com/autoparts/inventory-management/internal/notification"
	"github.com/autoparts/inventory-management/internal/upload"
)

type Handlers struct {
	Auth         *auth.Handler
	Inventory    *inventory.Handler
	Notification *notification.Handler
	Upload       *upload.Handler
	JWT          *jwtutil.Service
}

func NewRouter(h Handlers) http.Handler {
	r := chi.NewRouter()
	r.Use(middleware.RequestID)
	r.Use(middleware.RealIP)
	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)

	r.Get("/actuator/health", func(w http.ResponseWriter, _ *http.Request) {
		api.OK(w, map[string]string{"status": "UP"})
	})
	r.Get("/health", func(w http.ResponseWriter, _ *http.Request) {
		api.OK(w, map[string]string{"status": "UP"})
	})

	r.Route("/api/v1", func(r chi.Router) {
		r.Route("/auth", func(r chi.Router) {
			r.Post("/otp/request", h.Auth.RequestOTP)
			r.Post("/otp/verify", h.Auth.VerifyOTP)
			r.Post("/token/refresh", h.Auth.RefreshToken)

			r.Group(func(r chi.Router) {
				r.Use(api.Auth(h.JWT))
				r.Delete("/logout", h.Auth.Logout)
				r.Get("/profile", h.Auth.GetProfile)
				r.Put("/profile", h.Auth.UpdateProfile)
			})
		})

		r.Group(func(r chi.Router) {
			r.Use(api.Auth(h.JWT))

			r.Route("/inventory", func(r chi.Router) {
				r.Get("/", h.Inventory.List)
				r.Post("/", h.Inventory.Add)
				r.Get("/low-stock", h.Inventory.LowStock)
				r.Get("/history/{id}", h.Inventory.History)
				r.Get("/{id}", h.Inventory.Get)
				r.Put("/{id}", h.Inventory.Update)
				r.Delete("/{id}", h.Inventory.Delete)
				r.Patch("/{id}/quantity", h.Inventory.UpdateQuantity)
			})

			r.Route("/notifications", func(r chi.Router) {
				r.Get("/", h.Notification.List)
				r.Patch("/{id}/read", h.Notification.MarkRead)
			})

			r.Post("/uploads/presign", h.Upload.Presign)
		})
	})

	return r
}
