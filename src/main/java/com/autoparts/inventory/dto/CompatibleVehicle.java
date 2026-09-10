package com.autoparts.inventory.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One vehicle a part fits — just make (a.k.a. company / brand) and model, which is
 * all a shop owner will actually type. Stored as a JSON array element on
 * {@code inventory_items} and echoed back verbatim in part responses.
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
}
