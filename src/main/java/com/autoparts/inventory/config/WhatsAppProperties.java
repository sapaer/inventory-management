package com.autoparts.inventory.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppProperties {
    private String apiUrl;
    private String phoneNumberId;
    private String accessToken;
}
