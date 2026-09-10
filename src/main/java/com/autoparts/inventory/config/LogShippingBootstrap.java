package com.autoparts.inventory.config;

/**
 * Promotes the Better Stack log-shipping env vars to system properties <em>before</em>
 * Log4j2 initialises, so {@code log4j2-spring.xml} can switch the HTTP appender on with
 * a {@code SystemPropertyArbiter}. No token set → the appender stays detached and the
 * app logs only to console/file.
 *
 * <ul>
 *   <li>{@code LOG_SHIP_TOKEN} — Better Stack source token (required to enable)</li>
 *   <li>{@code LOG_SHIP_URL}   — ingest URL (default {@code https://in.logs.betterstack.com})</li>
 * </ul>
 */
public final class LogShippingBootstrap {
    private static final String DEFAULT_URL = "https://in.logs.betterstack.com";

    private LogShippingBootstrap() {}

    public static void applyToSystemProperties() {
        String token = firstNonBlank(System.getenv("LOG_SHIP_TOKEN"), System.getProperty("LOG_SHIP_TOKEN"));
        if (token == null) {
            return;
        }
        String url = firstNonBlank(System.getenv("LOG_SHIP_URL"), System.getProperty("LOG_SHIP_URL"));
        System.setProperty("log.ship.enabled", "true");
        System.setProperty("log.ship.token", token);
        System.setProperty("log.ship.url", url == null ? DEFAULT_URL : url);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }
}
