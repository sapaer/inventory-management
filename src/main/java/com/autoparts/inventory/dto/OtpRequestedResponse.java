package com.autoparts.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OtpRequestedResponse {
    private final String message;

    @JsonProperty("expires_in")
    private final String expiresIn;
}
