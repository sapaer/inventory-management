package jwtutil

import (
	"encoding/base64"
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

type Service struct {
	secret             []byte
	accessExpiryHours  int
}

func New(secret string, accessExpiryHours int) (*Service, error) {
	key, err := base64.StdEncoding.DecodeString(secret)
	if err != nil {
		key = []byte(secret)
	}
	if len(key) < 32 {
		return nil, fmt.Errorf("jwt secret must decode to at least 32 bytes")
	}
	return &Service{secret: key, accessExpiryHours: accessExpiryHours}, nil
}

func (s *Service) GenerateAccessToken(userID uuid.UUID, phone string) (string, error) {
	claims := jwt.MapClaims{
		"sub":   userID.String(),
		"phone": phone,
		"iat":   time.Now().Unix(),
		"exp":   time.Now().Add(time.Duration(s.accessExpiryHours) * time.Hour).Unix(),
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString(s.secret)
}

func (s *Service) ParseUserID(tokenString string) (uuid.UUID, error) {
	token, err := jwt.Parse(tokenString, func(t *jwt.Token) (any, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method")
		}
		return s.secret, nil
	})
	if err != nil || !token.Valid {
		return uuid.Nil, fmt.Errorf("invalid token")
	}
	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return uuid.Nil, fmt.Errorf("invalid claims")
	}
	sub, _ := claims["sub"].(string)
	id, err := uuid.Parse(sub)
	if err != nil {
		return uuid.Nil, fmt.Errorf("invalid subject")
	}
	return id, nil
}
