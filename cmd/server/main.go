package main

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/autoparts/inventory-management/internal/auth"
	"github.com/autoparts/inventory-management/internal/config"
	"github.com/autoparts/inventory-management/internal/inventory"
	"github.com/autoparts/inventory-management/internal/jwtutil"
	"github.com/autoparts/inventory-management/internal/notification"
	"github.com/autoparts/inventory-management/internal/server"
	"github.com/autoparts/inventory-management/internal/store"
	"github.com/autoparts/inventory-management/internal/upload"
)

func main() {
	slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stdout, nil)))

	cfg := config.Load()
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	pool, err := store.NewPostgres(ctx, cfg.DatabaseURL)
	if err != nil {
		slog.Error("postgres failed", "err", err)
		os.Exit(1)
	}
	defer pool.Close()

	if err := store.RunMigrations(ctx, pool, "migrations/000001_init.up.sql"); err != nil {
		slog.Error("migrations failed", "err", err)
		os.Exit(1)
	}

	cache, err := store.NewRedis(ctx, cfg.RedisURL)
	if err != nil {
		slog.Error("redis failed", "err", err)
		os.Exit(1)
	}
	defer cache.Close()

	jwtSvc, err := jwtutil.New(cfg.JWTSecret, cfg.JWTAccessExpiryHours)
	if err != nil {
		slog.Error("jwt init failed", "err", err)
		os.Exit(1)
	}

	s3Svc, err := upload.New(ctx, cfg.AWSS3Region, cfg.AWSAccessKey, cfg.AWSSecretKey, cfg.AWSS3Bucket, cfg.CloudFrontURL)
	if err != nil {
		slog.Error("s3 init failed", "err", err)
		os.Exit(1)
	}

	userRepo := auth.NewRepository(pool)
	invRepo := inventory.NewRepository(pool)
	notifRepo := notification.NewRepository(pool)

	wa := notification.NewWhatsApp(cfg.WhatsAppAPIURL, cfg.WhatsAppPhoneNumberID, cfg.WhatsAppAccessToken)
	fcm := notification.NewFCM(cfg.FirebaseCredentialsPath)

	authSvc := auth.NewService(userRepo, cache, jwtSvc, wa)
	notifSvc := notification.NewService(notifRepo, userRepo, wa, fcm)
	invSvc := inventory.NewService(invRepo, notifSvc)

	scheduler := notification.NewScheduler(invRepo, notifSvc, cache)
	scheduler.Start(ctx)

	router := server.NewRouter(server.Handlers{
		Auth:         auth.NewHandler(authSvc),
		Inventory:    inventory.NewHandler(invSvc),
		Notification: notification.NewHandler(notifSvc),
		Upload:       upload.NewHandler(s3Svc),
		JWT:          jwtSvc,
	})

	server := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      router,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
	}

	go func() {
		slog.Info("server started", "port", cfg.Port)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			slog.Error("server failed", "err", err)
			os.Exit(1)
		}
	}()

	<-ctx.Done()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = server.Shutdown(shutdownCtx)
	slog.Info("server stopped")
}
