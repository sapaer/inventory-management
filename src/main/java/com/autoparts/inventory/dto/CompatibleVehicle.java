package com.autoparts.inventory.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One vehicle a part fits. Stored as a JSON array element on {@code inventory_items}
 * and echoed back verbatim in part responses.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompatibleVehicle {
    @NotBlank
    @Size(max = 60)
    private String make;

    @Size(max = 60)
    private String model;

    @Size(max = 40)
    private String variant;

    @Min(1950)
    @Max(2100)
    private Integer yearFrom;

    @Min(1950)
    @Max(2100)
    private Integer yearTo;
}
