package com.autoparts.inventory.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LogShippingBootstrapTest {

    @AfterEach
    void clearProps() {
        System.clearProperty("LOG_SHIP_TOKEN");
        System.clearProperty("LOG_SHIP_URL");
        System.clearProperty("log.ship.enabled");
        System.clearProperty("log.ship.token");
        System.clearProperty("log.ship.url");
    }

    @Test
    void noTokenLeavesLoggingUntouched() {
        LogShippingBootstrap.applyToSystemProperties();

        assertNull(System.getProperty("log.ship.enabled"));
    }

    @Test
    void tokenEnablesShippingWithDefaultUrl() {
        System.setProperty("LOG_SHIP_TOKEN", "abc123");

        LogShippingBootstrap.applyToSystemProperties();

        assertEquals("true", System.getProperty("log.ship.enabled"));
        assertEquals("abc123", System.getProperty("log.ship.token"));
        assertEquals("https://in.logs.betterstack.com", System.getProperty("log.ship.url"));
    }

    @Test
    void customUrlIsHonoured() {
        System.setProperty("LOG_SHIP_TOKEN", "abc123");
        System.setProperty("LOG_SHIP_URL", "https://s123.eu.betterstackdata.com");

        LogShippingBootstrap.applyToSystemProperties();

        assertEquals("https://s123.eu.betterstackdata.com", System.getProperty("log.ship.url"));
    }
}
