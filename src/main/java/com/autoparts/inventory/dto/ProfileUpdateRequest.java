package com.autoparts.inventory.dto;

import com.autoparts.inventory.enums.BusinessType;
import com.autoparts.inventory.enums.VehicleCategory;

import java.util.List;

public record ProfileUpdateRequest(
        String name,
        String shopName,
        String email,
        BusinessType businessType,
        String address,
        String area,
        String city,
        String state,
        String pincode,
        Double geoLat,
        Double geoLng,
        List<VehicleCategory> vehicleCategories
) {}
