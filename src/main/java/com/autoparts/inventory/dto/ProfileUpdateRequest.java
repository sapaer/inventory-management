package com.autoparts.inventory.dto;

import com.autoparts.inventory.enums.BusinessType;
import com.autoparts.inventory.enums.VehicleCategory;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProfileUpdateRequest {
    private String name;
    private String shopName;
    private String email;
    private BusinessType businessType;
    private String address;
    private String area;
    private String city;
    private String state;
    private String pincode;
    private Double geoLat;
    private Double geoLng;
    private List<VehicleCategory> vehicleCategories;
}
