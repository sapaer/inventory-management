package com.autoparts.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        boolean logOtp,
        Jwt jwt,
        Aws aws,
        WhatsApp whatsapp,
        Sms sms
) {
    public record Jwt(String secret, int accessExpiryHours, int refreshExpiryDays) {}

    public record Aws(String s3Bucket, String s3Region, String accessKey, String secretKey, String cloudfrontUrl) {}

    public record WhatsApp(String apiUrl, String phoneNumberId, String accessToken) {}

    public record Sms(
            String provider,
            String authKey,
            String senderId,
            String accountSid,
            String authToken,
            String fromNumber
    ) {}
}
