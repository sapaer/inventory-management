package com.autoparts.inventory.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TwilioProperties {
    private String accountSid;
    private String authToken;
    private String fromNumber;
    private String whatsappFrom;
    private String otpContentSid;
    private String lowStockContentSid;
}
