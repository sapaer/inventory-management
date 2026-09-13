package com.autoparts.inventory.voice;

import com.autoparts.inventory.config.AppProperties;
import com.twilio.security.RequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Confirms an incoming voice webhook really came from Twilio before we act on it or
 * hand back TwiML. Without this, anyone could POST to these public, unauthenticated
 * endpoints — including to read back the co-founders' phone numbers from the
 * {@code /incoming} response.
 */
@Component
public class TwilioSignatureValidator {
    private static final Logger log = LoggerFactory.getLogger(TwilioSignatureValidator.class);

    private final AppProperties props;

    public TwilioSignatureValidator(AppProperties props) {
        this.props = props;
    }

    public boolean isValid(HttpServletRequest request) {
        String authToken = props.getTwilio() == null ? null : props.getTwilio().getAuthToken();
        if (authToken == null || authToken.isBlank()) {
            log.warn("voice webhook rejected: TWILIO_AUTH_TOKEN not configured");
            return false;
        }
        String signature = request.getHeader("X-Twilio-Signature");
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String url = request.getRequestURL().toString();
        boolean valid = new RequestValidator(authToken).validate(url, paramsOf(request), signature);
        if (!valid) {
            log.warn("voice webhook signature invalid url={}", url);
        }
        return valid;
    }

    /** Single-valued view of the POSTed form params, as Twilio's webhooks send them. */
    public static Map<String, String> paramsOf(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }
}
