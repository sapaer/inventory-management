package com.autoparts.inventory.dto;

import com.autoparts.inventory.enums.VehicleCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record AddPartRequest(
        @NotBlank @Size(max = 200) String partName,
        String localName,
        String specification,
        String description,
        @NotNull VehicleCategory vehicleCategory,
        String brand,
        String model,
        @NotNull Integer quantity,
        Integer minQuantity,
        BigDecimal sellingPrice,
        BigDecimal costPrice,
        @Size(max = 3) List<String> images
) {}
