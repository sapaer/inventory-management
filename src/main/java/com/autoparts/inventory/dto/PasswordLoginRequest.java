package com.autoparts.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordLoginRequest {
    @NotBlank
    private String phone;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;
}
