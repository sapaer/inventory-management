package com.autoparts.inventory.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Body for POST /api/v1/auth/accounts — an extra account on the caller's verified number. */
@Getter
@Setter
public class CreateAccountRequest {
    @Size(max = 50)
    private String firstName;

    @Size(max = 50)
    private String lastName;
}
