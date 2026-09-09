package com.autoparts.inventory;

import com.autoparts.inventory.config.DatabaseUrlParser;
import com.autoparts.inventory.config.LogShippingBootstrap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class InventoryApplication {
    public static void main(String[] args) {
        LogShippingBootstrap.applyToSystemProperties();
        DatabaseUrlParser.applyToSystemProperties();
        SpringApplication.run(InventoryApplication.class, args);
    }
}
