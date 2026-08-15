package config

import (
	"os"
	"strconv"

	"github.com/joho/godotenv"
)

type Config struct {
	Port                   string
	DatabaseURL            string
	RedisURL               string
	JWTSecret              string
	JWTAccessExpiryHours   int
	JWTRefreshExpiryDays   int
	AWSS3Bucket            string
	AWSS3Region            string
	AWSAccessKey           string
	AWSSecretKey           string
	CloudFrontURL          string
	WhatsAppAPIURL         string
	WhatsAppPhoneNumberID  string
	WhatsAppAccessToken    string
	FirebaseCredentialsPath string
}

func Load() Config {
	_ = godotenv.Load()

	return Config{
		Port:                    getenv("PORT", "8080"),
		DatabaseURL:             getenv("DATABASE_URL", "postgres://inventory:inventory@localhost:5432/inventory?sslmode=disable"),
		RedisURL:                getenv("REDIS_URL", "redis://localhost:6379"),
		JWTSecret:               getenv("JWT_SECRET", "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE="),
		JWTAccessExpiryHours:    getenvInt("JWT_ACCESS_EXPIRY_HOURS", 24),
		JWTRefreshExpiryDays:    getenvInt("JWT_REFRESH_EXPIRY_DAYS", 30),
		AWSS3Bucket:             getenv("AWS_S3_BUCKET", "local-bucket"),
		AWSS3Region:             getenv("AWS_S3_REGION", "ap-south-1"),
		AWSAccessKey:            getenv("AWS_ACCESS_KEY", "local"),
		AWSSecretKey:            getenv("AWS_SECRET_KEY", "local"),
		CloudFrontURL:           getenv("CLOUDFRONT_URL", "http://localhost"),
		WhatsAppAPIURL:          getenv("WHATSAPP_API_URL", "https://graph.facebook.com/v18.0"),
		WhatsAppPhoneNumberID:   getenv("WHATSAPP_PHONE_NUMBER_ID", ""),
		WhatsAppAccessToken:     getenv("WHATSAPP_ACCESS_TOKEN", ""),
		FirebaseCredentialsPath: getenv("FIREBASE_CREDENTIALS_PATH", ""),
	}
}

func getenv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func getenvInt(key string, fallback int) int {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return fallback
}
