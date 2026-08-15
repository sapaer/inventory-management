package api

import (
	"context"
	"net/http"
	"strings"

	"github.com/google/uuid"

	"github.com/autoparts/inventory-management/internal/apperr"
	"github.com/autoparts/inventory-management/internal/jwtutil"
)

type contextKey string

const userIDKey contextKey = "userID"

func UserIDFromContext(ctx context.Context) (uuid.UUID, bool) {
	id, ok := ctx.Value(userIDKey).(uuid.UUID)
	return id, ok
}

func Auth(jwtSvc *jwtutil.Service) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			header := r.Header.Get("Authorization")
			if header == "" || !strings.HasPrefix(header, "Bearer ") {
				FromError(w, apperr.Unauthorized("Missing or invalid token"))
				return
			}
			userID, err := jwtSvc.ParseUserID(strings.TrimPrefix(header, "Bearer "))
			if err != nil {
				FromError(w, apperr.Unauthorized("Missing or invalid token"))
				return
			}
			ctx := context.WithValue(r.Context(), userIDKey, userID)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

func RequireUser(w http.ResponseWriter, r *http.Request) (uuid.UUID, bool) {
	userID, ok := UserIDFromContext(r.Context())
	if !ok {
		FromError(w, apperr.Unauthorized("Missing or invalid token"))
		return uuid.Nil, false
	}
	return userID, true
}
