package com.autoparts.inventory.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class DatabaseUrlPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        String raw = DatabaseUrlParser.normalize(
                firstNonBlank(env.getProperty("DATABASE_URL"), System.getenv("DATABASE_URL")));
        Map<String, String> parsed = DatabaseUrlParser.parse(raw);
        if (parsed == null) {
            if ("true".equalsIgnoreCase(firstNonBlank(env.getProperty("RENDER"), System.getenv("RENDER")))) {
                throw new DatabaseUrlException(
                        "DATABASE_URL is missing on the Render web service. "
                                + "Set Key=DATABASE_URL and Value=postgresql://user:pass@host:port/db");
            }
            return;
        }
        env.getPropertySources().addFirst(new MapPropertySource("databaseUrl", new HashMap<>(parsed)));
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
