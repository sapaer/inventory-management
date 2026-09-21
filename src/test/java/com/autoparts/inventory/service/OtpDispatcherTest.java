package com.autoparts.inventory.service;

import com.autoparts.inventory.client.SmsClient;
import com.autoparts.inventory.client.WhatsAppClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpDispatcherTest {
    @Mock WhatsAppClient whatsapp;
    @Mock SmsClient sms;

    private OtpDispatcher dispatcher() {
        return new OtpDispatcher(whatsapp, sms);
    }

    @Test
    void sendsViaSmsWhenConfigured() {
        when(sms.configured()).thenReturn(true);

        dispatcher().sendOtp("8619544044", "123456");

        verify(sms).sendOtp("8619544044", "123456");
        verify(whatsapp, never()).sendOtp(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void fallsBackToWhatsAppWhenSmsSendFails() {
        when(sms.configured()).thenReturn(true);
        doThrow(new IllegalStateException("twilio sms: boom")).when(sms).sendOtp("8619544044", "123456");
        when(whatsapp.configured()).thenReturn(true);

        dispatcher().sendOtp("8619544044", "123456");

        verify(whatsapp).sendOtp("8619544044", "123456");
    }

    @Test
    void fallsBackToWhatsAppWhenSmsNotConfigured() {
        when(sms.configured()).thenReturn(false);
        when(whatsapp.configured()).thenReturn(true);

        dispatcher().sendOtp("8619544044", "123456");

        verify(whatsapp).sendOtp("8619544044", "123456");
        verify(sms, never()).sendOtp(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void throwsWhenNoChannelConfigured() {
        when(sms.configured()).thenReturn(false);
        when(whatsapp.configured()).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> dispatcher().sendOtp("8619544044", "123456"));
    }
}
