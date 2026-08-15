package store

import (
	"context"
	"fmt"
	"os"

	"github.com/jackc/pgx/v5/pgxpool"
)

func RunMigrations(ctx context.Context, pool *pgxpool.Pool, sqlPath string) error {
	if _, err := pool.Exec(ctx, `
		CREATE TABLE IF NOT EXISTS schema_migrations (
			version INTEGER PRIMARY KEY,
			applied_at TIMESTAMP NOT NULL DEFAULT NOW()
		)
	`); err != nil {
		return fmt.Errorf("create schema_migrations: %w", err)
	}

	var applied int
	if err := pool.QueryRow(ctx, `SELECT COUNT(*) FROM schema_migrations WHERE version = 1`).Scan(&applied); err != nil {
		return err
	}
	if applied > 0 {
		return nil
	}

	body, err := os.ReadFile(sqlPath)
	if err != nil {
		return fmt.Errorf("read migration: %w", err)
	}

	conn, err := pool.Acquire(ctx)
	if err != nil {
		return err
	}
	defer conn.Release()

	if _, err := conn.Conn().PgConn().Exec(ctx, string(body)).ReadAll(); err != nil {
		return fmt.Errorf("apply migration: %w", err)
	}
	if _, err := pool.Exec(ctx, `INSERT INTO schema_migrations (version) VALUES (1)`); err != nil {
		return err
	}
	return nil
}
