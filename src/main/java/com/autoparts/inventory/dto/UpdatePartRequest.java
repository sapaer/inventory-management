package com.autoparts.inventory.dto;

import com.autoparts.inventory.enums.VehicleCategory;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class UpdatePartRequest {
    private String partName;
    private String localName;
    private String specification;
    private String description;
    private VehicleCategory vehicleCategory;
    private String brand;
    private String model;
    private Integer minQuantity;
    private BigDecimal sellingPrice;
    private BigDecimal costPrice;
    private List<String> images;
}
