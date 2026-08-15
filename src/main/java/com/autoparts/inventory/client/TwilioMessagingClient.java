package com.autoparts.inventory.client;

import com.autoparts.inventory.config.AppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TwilioMessagingClient {
    private static final Logger log = LoggerFactory.getLogger(TwilioMessagingClient.class);

    private final AppProperties.Twilio cfg;
    private final ObjectMapper json;
    private final boolean ready;

    public TwilioMessagingClient(AppProperties props, ObjectMapper json) {
        this.cfg = props.twilio() == null
                ? new AppProperties.Twilio("", "", "", "", "", "")
                : props.twilio();
        this.json = json;
        this.ready = notBlank(cfg.accountSid()) && notBlank(cfg.authToken());
        if (ready) {
            Twilio.init(cfg.accountSid(), cfg.authToken());
            log.info("twilio client initialized");
        } else {
            log.warn("twilio credentials missing; SMS/WhatsApp via Twilio disabled");
        }
    }

    public boolean smsConfigured() {
        return ready && notBlank(cfg.fromNumber());
    }

    public boolean whatsappConfigured() {
        return ready && notBlank(cfg.whatsappFrom());
    }

    public void sendSms(String phone, String body) {
        if (!smsConfigured()) {
            throw new IllegalStateException("twilio sms not configured");
        }
        try {
            Message msg = Message.creator(
                    new PhoneNumber(toE164(phone)),
                    new PhoneNumber(cfg.fromNumber().trim()),
                    body
            ).create();
            failIfTwilioError(msg, "sms");
            log.info("twilio sms sent sid={} to={}", msg.getSid(), mask(phone));
        } catch (ApiException ex) {
            log.error("twilio sms failed code={} status={} msg={}", ex.getCode(), ex.getStatusCode(), ex.getMessage());
            throw new IllegalStateException("twilio sms: " + ex.getMessage());
        }
    }

    public void sendWhatsApp(String phone, String body, String contentSid, Map<String, String> variables) {
        if (!whatsappConfigured()) {
            throw new IllegalStateException("twilio whatsapp not configured");
        }
        try {
            PhoneNumber to = new PhoneNumber(toWhatsApp(phone));
            PhoneNumber from = new PhoneNumber(toWhatsAppFrom(cfg.whatsappFrom()));
            MessageCreator creator;
            boolean useTemplate = notBlank(contentSid) && contentSid.trim().toUpperCase().startsWith("HX");
            if (useTemplate) {
                creator = Message.creator(to, from, "").setBody(null).setContentSid(contentSid.trim());
                if (variables != null && !variables.isEmpty()) {
                    creator.setContentVariables(writeJson(variables));
                }
            } else {
                creator = Message.creator(to, from, body);
            }
            Message msg = creator.create();
            failIfTwilioError(msg, "whatsapp");
            log.info("twilio whatsapp sent sid={} to={}", msg.getSid(), mask(phone));
        } catch (ApiException ex) {
            log.error("twilio whatsapp failed code={} status={} msg={}", ex.getCode(), ex.getStatusCode(), ex.getMessage());
            throw new IllegalStateException("twilio whatsapp: " + ex.getMessage());
        }
    }

    public String otpContentSid() {
        return cfg.otpContentSid();
    }

    public String lowStockContentSid() {
        return cfg.lowStockContentSid();
    }

    static String toE164(String phone) {
        String digits = phone == null ? "" : phone.trim();
        if (digits.startsWith("+")) {
            return digits;
        }
        digits = digits.replaceAll("\\D", "");
        if (digits.startsWith("91") && digits.length() == 12) {
            return "+" + digits;
        }
        if (digits.length() == 10) {
            return "+91" + digits;
        }
        return "+" + digits;
    }

    static String toWhatsApp(String phone) {
        String e164 = toE164(phone);
        return e164.startsWith("whatsapp:") ? e164 : "whatsapp:" + e164;
    }

    static String toWhatsAppFrom(String from) {
        String value = from.trim();
        if (value.startsWith("whatsapp:")) {
            return value;
        }
        if (value.startsWith("+")) {
            return "whatsapp:" + value;
        }
        return "whatsapp:+" + value.replaceAll("\\D", "");
    }

    private String writeJson(Map<String, String> variables) {
        try {
            return json.writeValueAsString(variables);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("could not encode twilio content variables");
        }
    }

    private static void failIfTwilioError(Message msg, String channel) {
        if (msg.getErrorCode() != null) {
            throw new IllegalStateException(channel + " error " + msg.getErrorCode() + ": " + msg.getErrorMessage());
        }
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "******" + phone.substring(phone.length() - 4);
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }
}
