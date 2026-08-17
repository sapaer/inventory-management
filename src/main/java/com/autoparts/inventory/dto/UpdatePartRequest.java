package com.autoparts.inventory.dto;

import com.autoparts.inventory.enums.VehicleCategory;

import java.math.BigDecimal;
import java.util.List;

public record UpdatePartRequest(
        String partName,
        String localName,
        String specification,
        String description,
        VehicleCategory vehicleCategory,
        String brand,
        String model,
        Integer minQuantity,
        BigDecimal sellingPrice,
        BigDecimal costPrice,
        List<String> images
) {}
