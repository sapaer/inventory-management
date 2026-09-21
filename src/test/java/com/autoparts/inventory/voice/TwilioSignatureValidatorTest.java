package com.autoparts.inventory.voice;

import com.autoparts.inventory.config.AppProperties;
import com.autoparts.inventory.config.AwsProperties;
import com.autoparts.inventory.config.GoogleProperties;
import com.autoparts.inventory.config.JwtProperties;
import com.autoparts.inventory.config.SmsProperties;
import com.autoparts.inventory.config.TwilioProperties;
import com.autoparts.inventory.config.WhatsAppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwilioSignatureValidatorTest {
    private static final String AUTH_TOKEN = "test-auth-token";
    private static final String URL = "https://api.example.com/api/v1/voice/incoming";

    private static AppProperties propsWithToken(String token) {
        return new AppProperties(
                true, false, "000000",
                new JwtProperties("QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=", 24, 30),
                new AwsProperties("b", "r", "k", "s", "u"),
                new WhatsAppProperties("u", "", ""),
                new SmsProperties("twilio", "", ""),
                new TwilioProperties("AC123", token, "+15550000", "whatsapp:+15550000", "", ""),
                new GoogleProperties(""));
    }

    private static MockHttpServletRequest requestWithParams(Map<String, String> params, String signature) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/voice/incoming");
        req.setScheme("https");
        req.setServerName("api.example.com");
        req.setServerPort(443);
        req.setRequestURI("/api/v1/voice/incoming");
        params.forEach(req::setParameter);
        if (signature != null) {
            req.addHeader("X-Twilio-Signature", signature);
        }
        return req;
    }

    /** Independent re-implementation of Twilio's documented HMAC-SHA1 signing algorithm. */
    private static String sign(String authToken, String url, Map<String, String> params) throws Exception {
        StringBuilder data = new StringBuilder(url);
        new TreeMap<>(params).forEach((k, v) -> data.append(k).append(v));
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void acceptsACorrectlySignedRequest() throws Exception {
        Map<String, String> params = Map.of("CallSid", "CA123", "From", "+919876543210");
        String signature = sign(AUTH_TOKEN, URL, params);

        boolean valid = new TwilioSignatureValidator(propsWithToken(AUTH_TOKEN))
                .isValid(requestWithParams(params, signature));

        assertTrue(valid);
    }

    @Test
    void rejectsATamperedSignature() throws Exception {
        Map<String, String> params = Map.of("CallSid", "CA123", "From", "+919876543210");
        String signature = sign(AUTH_TOKEN, URL, params);

        // A field changed after signing (e.g. someone replaying/editing the POST).
        MockHttpServletRequest tampered = requestWithParams(Map.of("CallSid", "CA999", "From", "+919876543210"), signature);

        assertFalse(new TwilioSignatureValidator(propsWithToken(AUTH_TOKEN)).isValid(tampered));
    }

    @Test
    void rejectsWhenSignatureHeaderMissing() {
        MockHttpServletRequest req = requestWithParams(Map.of("CallSid", "CA123"), null);

        assertFalse(new TwilioSignatureValidator(propsWithToken(AUTH_TOKEN)).isValid(req));
    }

    @Test
    void rejectsWhenAuthTokenNotConfigured() throws Exception {
        Map<String, String> params = Map.of("CallSid", "CA123");
        String signature = sign(AUTH_TOKEN, URL, params);

        assertFalse(new TwilioSignatureValidator(propsWithToken("")).isValid(requestWithParams(params, signature)));
    }
}
