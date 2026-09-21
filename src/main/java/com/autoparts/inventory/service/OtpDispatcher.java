package com.autoparts.inventory.service;

import com.autoparts.inventory.client.SmsClient;
import com.autoparts.inventory.client.WhatsAppClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OtpDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OtpDispatcher.class);
    private final WhatsAppClient whatsapp;
    private final SmsClient sms;

    public OtpDispatcher(WhatsAppClient whatsapp, SmsClient sms) {
        this.whatsapp = whatsapp;
        this.sms = sms;
    }

    public void sendOtp(String phone, String otp) {
        if (sms.configured()) {
            try {
                sms.sendOtp(phone, otp);
                log.info("otp delivered via sms phone={}", phone);
                return;
            } catch (Exception ex) {
                log.error("sms otp failed, falling back to whatsapp phone={}", phone, ex);
            }
        } else {
            log.warn("sms not configured, trying whatsapp");
        }
        if (whatsapp.configured()) {
            whatsapp.sendOtp(phone, otp);
            log.info("otp delivered via whatsapp phone={}", phone);
            return;
        }
        throw new IllegalStateException("no otp delivery channel available");
    }
}
