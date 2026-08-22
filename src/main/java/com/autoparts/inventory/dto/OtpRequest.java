package com.autoparts.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OtpRequest {
    @NotBlank
    private String phone;
}
