package com.autoparts.inventory.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DatabaseUrlParser {
    private static final Logger log = LoggerFactory.getLogger(DatabaseUrlParser.class);

    private DatabaseUrlParser() {}

    public static void applyToSystemProperties() {
        Map<String, String> props = parse(firstNonBlank(System.getenv("DATABASE_URL"), System.getProperty("DATABASE_URL")));
        if (props == null) {
            if ("true".equalsIgnoreCase(System.getenv("RENDER"))) {
                throw fail("DATABASE_URL is missing on the Render web service. "
                        + "Set Key=DATABASE_URL and Value=postgresql://user:pass@host:port/db");
            }
            return;
        }
        props.forEach(System::setProperty);
        log.info("datasource configured from DATABASE_URL jdbc={}", redact(props.get("spring.datasource.url")));
    }

    public static Map<String, String> parse(String raw) {
        String value = normalize(raw);
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.startsWith("jdbc:")) {
            Map<String, String> jdbc = new LinkedHashMap<>();
            jdbc.put("spring.datasource.url", value);
            return jdbc;
        }
        try {
            URI uri = URI.create(value.replaceFirst("^postgres(ql)?:", "http:"));
            String userInfo = uri.getUserInfo();
            if (userInfo == null || !userInfo.contains(":")) {
                throw fail("DATABASE_URL must include username and password");
            }
            int idx = userInfo.indexOf(':');
            String user = URLDecoder.decode(userInfo.substring(0, idx), StandardCharsets.UTF_8);
            String pass = URLDecoder.decode(userInfo.substring(idx + 1), StandardCharsets.UTF_8);
            if (user.isBlank() || pass.isBlank()) {
                throw fail("DATABASE_URL username/password cannot be blank");
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw fail("DATABASE_URL must include a hostname");
            }
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String path = uri.getPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                throw fail("DATABASE_URL must include a database name");
            }
            String jdbc = "jdbc:postgresql://" + host + ":" + port + path;
            String query = uri.getQuery();
            if (query != null && !query.isBlank()) {
                jdbc += "?" + query;
            } else if (!"localhost".equals(host) && !"127.0.0.1".equals(host)) {
                jdbc += "?sslmode=require";
            }
            Map<String, String> props = new LinkedHashMap<>();
            props.put("spring.datasource.url", jdbc);
            props.put("spring.datasource.username", user);
            props.put("spring.datasource.password", pass);
            log.info("parsed DATABASE_URL host={} port={} db={}", host, port, path.substring(1));
            return props;
        } catch (DatabaseUrlException ex) {
            throw ex;
        } catch (Exception ex) {
            throw fail("Could not parse DATABASE_URL: " + ex.getMessage(), ex);
        }
    }

    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.regionMatches(true, 0, "DATABASE_URL=", 0, "DATABASE_URL=".length())) {
            value = value.substring("DATABASE_URL=".length()).trim();
        }
        return value;
    }

    private static String redact(String jdbc) {
        if (jdbc == null) {
            return "";
        }
        return jdbc.replaceAll("//[^@]*@", "//****@");
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static DatabaseUrlException fail(String message) {
        log.error(message);
        return new DatabaseUrlException(message);
    }

    private static DatabaseUrlException fail(String message, Throwable cause) {
        log.error(message, cause);
        return new DatabaseUrlException(message, cause);
    }
}
