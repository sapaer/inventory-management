package com.autoparts.inventory.dto;

import com.autoparts.inventory.enums.VehicleCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class AddPartRequest {
    @NotBlank
    @Size(max = 200)
    private String partName;

    private String localName;
    private String specification;
    private String description;

    @NotNull
    private VehicleCategory vehicleCategory;

    private String brand;
    private String model;

    @NotNull
    private Integer quantity;

    private Integer minQuantity;
    private BigDecimal sellingPrice;
    private BigDecimal costPrice;

    @Size(max = 3)
    private List<String> images;
}
