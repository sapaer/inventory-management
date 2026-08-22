package com.autoparts.inventory.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse implements AuthResult {
    private final String accessToken;
    private final String refreshToken;
    private final UserResponse user;

    @JsonProperty("isNewUser")
    private final boolean newUser;
}
