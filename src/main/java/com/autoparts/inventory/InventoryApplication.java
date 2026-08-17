package com.autoparts.inventory;

import com.autoparts.inventory.config.DatabaseUrlParser;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class InventoryApplication {
    public static void main(String[] args) {
        DatabaseUrlParser.applyToSystemProperties();
        SpringApplication.run(InventoryApplication.class, args);
    }
}
