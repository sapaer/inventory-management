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
        if (whatsapp.configured()) {
            try {
                whatsapp.sendOtp(phone, otp);
                log.info("otp delivered via whatsapp phone={}", phone);
                return;
            } catch (Exception ex) {
                log.error("whatsapp otp failed, falling back to sms phone={}", phone, ex);
            }
        } else {
            log.warn("whatsapp not configured, trying sms");
        }
        if (sms.configured()) {
            sms.sendOtp(phone, otp);
            log.info("otp delivered via sms phone={}", phone);
            return;
        }
        throw new IllegalStateException("no otp delivery channel available");
    }
}
