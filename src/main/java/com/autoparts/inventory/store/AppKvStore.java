package com.autoparts.inventory.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Postgres-backed ephemeral store (OTP, refresh sessions, short locks).
 * Replaces Redis so Phase 1 only needs Postgres.
 */
@Component
public class AppKvStore {
    private final JdbcTemplate jdbc;

    public AppKvStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String get(String key) {
        purgeExpired(key);
        List<String> rows = jdbc.query(
                "SELECT value FROM app_kv_store WHERE key = ? AND (expires_at IS NULL OR expires_at > now())",
                (rs, i) -> rs.getString(1),
                key
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void set(String key, String value, Duration ttl) {
        Timestamp expires = ttl == null ? null : Timestamp.from(Instant.now().plus(ttl));
        jdbc.update(
                """
                INSERT INTO app_kv_store(key, value, expires_at) VALUES (?, ?, ?)
                ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, expires_at = EXCLUDED.expires_at
                """,
                key,
                value,
                expires
        );
    }

    @Transactional
    public long incr(String key) {
        List<String> returned = jdbc.query(
                """
                INSERT INTO app_kv_store AS t (key, value, expires_at)
                VALUES (?, '1', NULL)
                ON CONFLICT (key) DO UPDATE SET
                    value = CASE
                        WHEN t.expires_at IS NOT NULL AND t.expires_at <= now() THEN '1'
                        ELSE (COALESCE(NULLIF(t.value, ''), '0')::bigint + 1)::text
                    END,
                    expires_at = CASE
                        WHEN t.expires_at IS NOT NULL AND t.expires_at <= now() THEN NULL
                        ELSE t.expires_at
                    END
                RETURNING value
                """,
                (rs, i) -> rs.getString(1),
                key
        );
        if (returned.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(returned.get(0));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public void expire(String key, Duration ttl) {
        if (ttl == null) {
            return;
        }
        Timestamp expires = Timestamp.from(Instant.now().plus(ttl));
        jdbc.update(
                """
                UPDATE app_kv_store SET expires_at = ?
                WHERE key = ? AND (expires_at IS NULL OR expires_at > now())
                """,
                expires,
                key
        );
    }

    public void delete(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        for (String key : keys) {
            if (key != null && !key.isBlank()) {
                jdbc.update("DELETE FROM app_kv_store WHERE key = ?", key);
            }
        }
    }

    public boolean exists(String key) {
        purgeExpired(key);
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(1) FROM app_kv_store WHERE key = ? AND (expires_at IS NULL OR expires_at > now())",
                Integer.class,
                key
        );
        return n != null && n > 0;
    }

    public boolean ping() {
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (Exception e) {
            return false;
        }
    }

    private void purgeExpired(String key) {
        jdbc.update("DELETE FROM app_kv_store WHERE key = ? AND expires_at IS NOT NULL AND expires_at <= now()", key);
    }

    /** Optional maintenance — remove all expired rows. */
    public int purgeAllExpired() {
        return jdbc.update("DELETE FROM app_kv_store WHERE expires_at IS NOT NULL AND expires_at <= now()");
    }
}
