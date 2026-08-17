package com.autoparts.inventory.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseUrlPostProcessorTest {
    @Test
    void missingUrlOnRenderThrowsDatabaseUrlException() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("RENDER", "true");

        DatabaseUrlException ex = assertThrows(
                DatabaseUrlException.class,
                () -> new DatabaseUrlPostProcessor().postProcessEnvironment(env, null)
        );
        assertEquals(true, ex.getMessage().contains("DATABASE_URL is missing"));
    }

    @Test
    void urlWithoutUserThrowsDatabaseUrlException() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("DATABASE_URL", "postgres://dpg-example:5432/example_spaer_instance");

        assertThrows(
                DatabaseUrlException.class,
                () -> new DatabaseUrlPostProcessor().postProcessEnvironment(env, null)
        );
    }

    @Test
    void validUrlSetsDatasourceProperties() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("DATABASE_URL", "postgres://shop:secret@db.example:5432/parts?sslmode=require");

        new DatabaseUrlPostProcessor().postProcessEnvironment(env, null);

        assertEquals("jdbc:postgresql://db.example:5432/parts?sslmode=require", env.getProperty("spring.datasource.url"));
        assertEquals("shop", env.getProperty("spring.datasource.username"));
        assertEquals("secret", env.getProperty("spring.datasource.password"));
    }

    @Test
    void postgresqlUrlUsesExplicitPort() {
        var props = DatabaseUrlParser.parse(
                "postgresql://spaer_user:secret@dpg-example-a:5042/spaer?sslmode=require");
        assertEquals("jdbc:postgresql://dpg-example-a:5042/spaer?sslmode=require", props.get("spring.datasource.url"));
        assertEquals("spaer_user", props.get("spring.datasource.username"));
    }
}
