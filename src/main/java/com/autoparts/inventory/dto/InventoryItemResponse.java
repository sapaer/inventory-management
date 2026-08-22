package com.autoparts.inventory.dto;

import com.autoparts.inventory.enums.VehicleCategory;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InventoryItemResponse {
    private final UUID id;
    private final String partName;
    private final String localName;
    private final String specification;
    private final String description;
    private final VehicleCategory vehicleCategory;
    private final String brand;
    private final String model;
    private final int quantity;
    private final int minQuantity;
    private final BigDecimal sellingPrice;
    private final List<String> images;
    private final String stockStatus;

    @JsonProperty("isActive")
    private final boolean active;

    @JsonProperty("isDuplicate")
    private final Boolean duplicate;

    private final Instant createdAt;
    private final Instant updatedAt;
}
