package com.autoparts.inventory.client;

import com.autoparts.inventory.config.AppProperties;
import com.autoparts.inventory.config.SmsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Component
public class SmsClient {
    private static final Logger log = LoggerFactory.getLogger(SmsClient.class);
    private final SmsProperties cfg;
    private final TwilioMessagingClient twilio;
    private final RestTemplate http = new RestTemplate();

    public SmsClient(AppProperties props, TwilioMessagingClient twilio) {
        this.cfg = props.getSms();
        this.twilio = twilio;
    }

    public boolean configured() {
        if (useTwilio()) {
            return twilio.smsConfigured();
        }
        return notBlank(cfg.getAuthKey());
    }

    public void sendOtp(String phone, String otp) {
        send(phone, "Your inventory login OTP is " + otp + ". It expires in 5 minutes.", otp);
    }

    public void sendLowStockAlert(String phone, String partName, int qty) {
        send(phone, partName + " is low on stock — only " + qty + " units left.", null);
    }

    private void send(String phone, String msg, String otp) {
        if (!configured()) {
            throw new IllegalStateException("sms not configured");
        }
        if (useTwilio()) {
            twilio.sendSms(phone, msg);
            return;
        }
        sendMsg91(phone, msg, otp);
    }

    private void sendMsg91(String phone, String msg, String otp) {
        if (!notBlank(cfg.getSenderId()) || otp == null) {
            String url = UriComponentsBuilder.fromHttpUrl("https://api.msg91.com/api/sendhttp.php")
                    .queryParam("authkey", cfg.getAuthKey())
                    .queryParam("mobiles", "91" + phone)
                    .queryParam("message", msg)
                    .queryParam("route", "4")
                    .queryParam("country", "91")
                    .toUriString();
            exchange(url, new HttpEntity<>(new HttpHeaders()));
            return;
        }
        Map<String, Object> payload = Map.of(
                "template_id", cfg.getSenderId(),
                "short_url", "0",
                "recipients", List.of(Map.of("mobiles", "91" + phone, "OTP", otp, "otp", otp))
        );
        HttpHeaders headers = new HttpHeaders();
        headers.set("authkey", cfg.getAuthKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        exchange("https://control.msg91.com/api/v5/flow/", new HttpEntity<>(payload, headers));
    }

    private void exchange(String url, HttpEntity<?> entity) {
        try {
            if (entity.getBody() == null) {
                http.getForEntity(url, String.class);
            } else {
                http.postForEntity(url, entity, String.class);
            }
            log.info("sms sent provider={}", provider());
        } catch (RestClientResponseException ex) {
            log.error("sms send failed provider={} status={} body={}", provider(), ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new IllegalStateException("sms status " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString());
        }
    }

    private boolean useTwilio() {
        return !"msg91".equals(provider());
    }

    private String provider() {
        return cfg.getProvider() == null || cfg.getProvider().isBlank() ? "twilio" : cfg.getProvider().trim().toLowerCase();
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }
}
