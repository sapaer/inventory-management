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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WhatsAppClient {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppClient.class);
    private final AppProperties.WhatsApp cfg;
    private final RestTemplate http = new RestTemplate();

    public WhatsAppClient(AppProperties props) {
        this.cfg = props.whatsapp();
    }

    public boolean configured() {
        return cfg.phoneNumberId() != null && !cfg.phoneNumberId().isBlank()
                && cfg.accessToken() != null && !cfg.accessToken().isBlank();
    }

    public void sendOtp(String phone, String otp) {
        List<Map<String, String>> otpParam = List.of(Map.of("type", "text", "text", otp));
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("type", "button");
        button.put("sub_type", "url");
        button.put("index", "0");
        button.put("parameters", otpParam);
        sendTemplate(phone, "otp_delivery", otpParam, button);
    }

    public void sendLowStockAlert(String phone, String partName, int qty) {
        if (!configured()) {
            log.warn("whatsapp credentials missing, skipping low stock alert");
            return;
        }
        sendTemplate(phone, "low_stock_alert", List.of(
                Map.of("type", "text", "text", partName),
                Map.of("type", "text", "text", String.valueOf(qty))
        ));
    }

    @SafeVarargs
    private void sendTemplate(String phone, String templateName, List<Map<String, String>> params, Map<String, Object>... extra) {
        if (!configured()) {
            throw new IllegalStateException("whatsapp not configured");
        }
        List<Map<String, Object>> components = new ArrayList<>();
        components.add(Map.of("type", "body", "parameters", params));
        if (extra != null) {
            components.addAll(List.of(extra));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", "91" + phone);
        body.put("type", "template");
        body.put("template", Map.of(
                "name", templateName,
                "language", Map.of("code", "en_IN"),
                "components", components
        ));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(cfg.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        String url = cfg.apiUrl() + "/" + cfg.phoneNumberId() + "/messages";
        try {
            http.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        } catch (RestClientResponseException ex) {
            log.error("whatsapp send failed status={} template={} body={}", ex.getStatusCode().value(), templateName, ex.getResponseBodyAsString());
            throw new IllegalStateException("whatsapp status " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString());
        }
    }
}
