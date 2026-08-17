package com.autoparts.inventory.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class DatabaseUrlPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        String raw = normalize(firstNonBlank(env.getProperty("DATABASE_URL"), System.getenv("DATABASE_URL")));
        boolean onRender = "true".equalsIgnoreCase(firstNonBlank(env.getProperty("RENDER"), System.getenv("RENDER")));
        if (raw == null || raw.isBlank()) {
            if (onRender) {
                throw new IllegalStateException(
                        "DATABASE_URL is missing on the Render web service. "
                                + "Set Key=DATABASE_URL and Value=postgres://... (do not include DATABASE_URL= in the value).");
            }
            return;
        }
        if (raw.startsWith("jdbc:")) {
            Map<String, Object> jdbcProps = new HashMap<>();
            jdbcProps.put("spring.datasource.url", raw);
            env.getPropertySources().addFirst(new MapPropertySource("databaseUrl", jdbcProps));
            return;
        }
        try {
            URI uri = URI.create(raw.replaceFirst("^postgres(ql)?:", "http:"));
            String user = "inventory";
            String pass = "inventory";
            String userInfo = uri.getUserInfo();
            if (userInfo != null && userInfo.contains(":")) {
                int idx = userInfo.indexOf(':');
                user = URLDecoder.decode(userInfo.substring(0, idx), StandardCharsets.UTF_8);
                pass = URLDecoder.decode(userInfo.substring(idx + 1), StandardCharsets.UTF_8);
            }
            String host = uri.getHost() == null ? "localhost" : uri.getHost();
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String path = uri.getPath() == null ? "/inventory" : uri.getPath();
            String jdbc = "jdbc:postgresql://" + host + ":" + port + path;
            String query = uri.getQuery();
            if (query != null && !query.isBlank()) {
                jdbc += "?" + query;
            } else if (!"localhost".equals(host) && !"127.0.0.1".equals(host)) {
                jdbc += "?sslmode=require";
            }
            Map<String, Object> props = new HashMap<>();
            props.put("spring.datasource.url", jdbc);
            props.put("spring.datasource.username", user);
            props.put("spring.datasource.password", pass);
            env.getPropertySources().addFirst(new MapPropertySource("databaseUrl", props));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not parse DATABASE_URL: " + ex.getMessage(), ex);
        }
    }

    private static String normalize(String raw) {
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

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
