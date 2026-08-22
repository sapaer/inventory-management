package com.autoparts.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OtpVerifyRequest {
    @NotBlank
    private String phone;

    @NotBlank
    @Size(min = 6, max = 6)
    private String otp;
}
