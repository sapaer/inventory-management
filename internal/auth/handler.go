package auth

import (
	"net/http"
	"regexp"

	"github.com/go-playground/validator/v10"

	"github.com/autoparts/inventory-management/internal/api"
	"github.com/autoparts/inventory-management/internal/apperr"
)

var indianPhone = regexp.MustCompile(`^[6-9]\d{9}$`)

type Handler struct {
	svc      *Service
	validate *validator.Validate
}

func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc, validate: validator.New()}
}

type otpRequest struct {
	Phone string `json:"phone" validate:"required"`
}

type otpVerify struct {
	Phone string `json:"phone" validate:"required"`
	OTP   string `json:"otp" validate:"required,len=6"`
}

type refreshRequest struct {
	RefreshToken string `json:"refresh_token"`
}

func (h *Handler) RequestOTP(w http.ResponseWriter, r *http.Request) {
	var req otpRequest
	if err := api.DecodeJSON(r, &req); err != nil {
		api.FromError(w, err)
		return
	}
	if err := h.validate.Struct(req); err != nil {
		api.FromError(w, err)
		return
	}
	if !indianPhone.MatchString(req.Phone) {
		api.FromError(w, apperr.BadRequest("VALIDATION_ERROR", "Enter a valid 10-digit Indian mobile number"))
		return
	}
	if err := h.svc.RequestOTP(r.Context(), req.Phone); err != nil {
		api.FromError(w, err)
		return
	}
	api.OK(w, map[string]string{"message": "OTP sent", "expires_in": "300"})
}

func (h *Handler) VerifyOTP(w http.ResponseWriter, r *http.Request) {
	var req otpVerify
	if err := api.DecodeJSON(r, &req); err != nil {
		api.FromError(w, err)
		return
	}
	if err := h.validate.Struct(req); err != nil {
		api.FromError(w, err)
		return
	}
	if !indianPhone.MatchString(req.Phone) {
		api.FromError(w, apperr.BadRequest("VALIDATION_ERROR", "Enter a valid 10-digit Indian mobile number"))
		return
	}
	resp, err := h.svc.VerifyOTP(r.Context(), req.Phone, req.OTP)
	if err != nil {
		api.FromError(w, err)
		return
	}
	api.OK(w, resp)
}

func (h *Handler) RefreshToken(w http.ResponseWriter, r *http.Request) {
	var req refreshRequest
	if err := api.DecodeJSON(r, &req); err != nil {
		api.FromError(w, err)
		return
	}
	tokens, err := h.svc.RefreshToken(r.Context(), req.RefreshToken)
	if err != nil {
		api.FromError(w, err)
		return
	}
	api.OK(w, tokens)
}

func (h *Handler) Logout(w http.ResponseWriter, r *http.Request) {
	userID, ok := api.RequireUser(w, r)
	if !ok {
		return
	}
	if err := h.svc.Logout(r.Context(), userID); err != nil {
		api.FromError(w, err)
		return
	}
	api.OK(w, nil)
}

func (h *Handler) GetProfile(w http.ResponseWriter, r *http.Request) {
	userID, ok := api.RequireUser(w, r)
	if !ok {
		return
	}
	dto, err := h.svc.GetProfile(r.Context(), userID)
	if err != nil {
		api.FromError(w, err)
		return
	}
	api.OK(w, dto)
}

func (h *Handler) UpdateProfile(w http.ResponseWriter, r *http.Request) {
	userID, ok := api.RequireUser(w, r)
	if !ok {
		return
	}
	var dto ProfileUpdate
	if err := api.DecodeJSON(r, &dto); err != nil {
		api.FromError(w, err)
		return
	}
	out, err := h.svc.UpdateProfile(r.Context(), userID, dto)
	if err != nil {
		api.FromError(w, err)
		return
	}
	api.OK(w, out)
}
