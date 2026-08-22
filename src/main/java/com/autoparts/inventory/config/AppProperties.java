package com.autoparts.inventory.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private boolean logOtp;
    private boolean devOtpBypass;
    private String devOtpCode;
    private JwtProperties jwt;
    private AwsProperties aws;
    private WhatsAppProperties whatsapp;
    private SmsProperties sms;
    private TwilioProperties twilio;
    private GoogleProperties google;

    public String effectiveDevOtpCode() {
        if (devOtpCode == null || devOtpCode.isBlank()) {
            return "000000";
        }
        return devOtpCode.trim();
    }
}
