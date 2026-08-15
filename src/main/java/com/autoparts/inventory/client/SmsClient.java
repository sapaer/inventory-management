package com.autoparts.inventory.client;

import com.autoparts.inventory.config.AppProperties;
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
    private final AppProperties.Sms cfg;
    private final RestTemplate http = new RestTemplate();

    public SmsClient(AppProperties props) {
        this.cfg = props.sms();
    }

    public boolean configured() {
        String provider = provider();
        if ("msg91".equals(provider)) {
            return notBlank(cfg.authKey());
        }
        if ("twilio".equals(provider)) {
            return notBlank(cfg.accountSid()) && notBlank(cfg.authToken()) && notBlank(cfg.fromNumber());
        }
        return false;
    }

    public void sendOtp(String phone, String otp) {
        if (!configured()) {
            throw new IllegalStateException("sms not configured");
        }
        String msg = "Your inventory login OTP is " + otp + ". It expires in 5 minutes.";
        if ("twilio".equals(provider())) {
            sendTwilio(phone, msg);
        } else {
            sendMsg91(phone, msg, otp);
        }
    }

    private void sendMsg91(String phone, String msg, String otp) {
        if (!notBlank(cfg.senderId())) {
            String url = UriComponentsBuilder.fromHttpUrl("https://api.msg91.com/api/sendhttp.php")
                    .queryParam("authkey", cfg.authKey())
                    .queryParam("mobiles", "91" + phone)
                    .queryParam("message", msg)
                    .queryParam("route", "4")
                    .queryParam("country", "91")
                    .toUriString();
            exchange(url, new HttpEntity<>(new HttpHeaders()));
            return;
        }
        Map<String, Object> payload = Map.of(
                "template_id", cfg.senderId(),
                "short_url", "0",
                "recipients", List.of(Map.of("mobiles", "91" + phone, "OTP", otp, "otp", otp))
        );
        HttpHeaders headers = new HttpHeaders();
        headers.set("authkey", cfg.authKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        exchange("https://control.msg91.com/api/v5/flow/", new HttpEntity<>(payload, headers));
    }

    private void sendTwilio(String phone, String msg) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(cfg.accountSid(), cfg.authToken());
        String form = "To=%2B91" + phone + "&From=" + cfg.fromNumber() + "&Body=" + java.net.URLEncoder.encode(msg, java.nio.charset.StandardCharsets.UTF_8);
        String url = "https://api.twilio.com/2010-04-01/Accounts/" + cfg.accountSid() + "/Messages.json";
        exchange(url, new HttpEntity<>(form, headers));
    }

    private void exchange(String url, HttpEntity<?> entity) {
        try {
            if (entity.getBody() == null) {
                http.getForEntity(url, String.class);
            } else {
                http.postForEntity(url, entity, String.class);
            }
            log.info("sms otp sent provider={}", provider());
        } catch (RestClientResponseException ex) {
            log.error("sms send failed provider={} status={} body={}", provider(), ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new IllegalStateException("sms status " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString());
        }
    }

    private String provider() {
        return cfg.provider() == null ? "msg91" : cfg.provider().trim().toLowerCase();
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }
}
