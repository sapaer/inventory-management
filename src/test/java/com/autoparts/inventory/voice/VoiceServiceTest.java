package com.autoparts.inventory.voice;

import com.autoparts.inventory.client.TwilioMessagingClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoiceServiceTest {
    @Mock TwilioMessagingClient twilio;

    private static SupportLineProperties configured() {
        SupportLineProperties p = new SupportLineProperties();
        p.setEnabled(true);
        p.setNumbers(List.of("+911111111111", "+912222222222"));
        p.setRingTimeoutSeconds(20);
        p.setVoicemailEnabled(true);
        p.setVoicemailMaxLengthSeconds(120);
        return p;
    }

    @Test
    void incomingRingsEveryConfiguredNumber() {
        String xml = new VoiceService(configured(), twilio).incoming();

        assertTrue(xml.contains("<Number>+911111111111</Number>"));
        assertTrue(xml.contains("<Number>+912222222222</Number>"));
        assertTrue(xml.contains("timeout=\"20\""));
        assertTrue(xml.contains("action=\"/api/v1/voice/no-answer\""));
    }

    @Test
    void incomingSaysNotSetUpWhenUnconfigured() {
        String xml = new VoiceService(new SupportLineProperties(), twilio).incoming();

        assertFalse(xml.contains("<Dial"));
        assertTrue(xml.contains("not set up"));
        assertTrue(xml.contains("<Hangup/>"));
    }

    @Test
    void dialFinishedJustHangsUpWhenAnswered() {
        String xml = new VoiceService(configured(), twilio).dialFinished(Map.of("DialCallStatus", "completed"));

        assertTrue(xml.contains("<Hangup/>"));
        assertFalse(xml.contains("<Record"));
    }

    @Test
    void dialFinishedOffersVoicemailWhenNoAnswer() {
        String xml = new VoiceService(configured(), twilio).dialFinished(Map.of("DialCallStatus", "no-answer"));

        assertTrue(xml.contains("<Record"));
        assertTrue(xml.contains("action=\"/api/v1/voice/voicemail-complete\""));
    }

    @Test
    void dialFinishedSkipsVoicemailWhenDisabled() {
        SupportLineProperties p = configured();
        p.setVoicemailEnabled(false);

        String xml = new VoiceService(p, twilio).dialFinished(Map.of("DialCallStatus", "busy"));

        assertFalse(xml.contains("<Record"));
        assertTrue(xml.contains("<Hangup/>"));
    }

    @Test
    void voicemailCompleteNotifiesEveryFounderBySms() {
        when(twilio.smsConfigured()).thenReturn(true);
        Map<String, String> params = Map.of(
                "RecordingUrl", "https://api.twilio.com/recordings/RE123",
                "From", "+919876543210",
                "RecordingDuration", "18");

        String xml = new VoiceService(configured(), twilio).voicemailComplete(params);

        verify(twilio).sendSms(eq("+911111111111"), contains("+919876543210"));
        verify(twilio).sendSms(eq("+912222222222"), contains("https://api.twilio.com/recordings/RE123"));
        assertTrue(xml.contains("<Hangup/>"));
    }

    @Test
    void voicemailCompleteFallsBackToWhatsAppWhenSmsNotConfigured() {
        when(twilio.smsConfigured()).thenReturn(false);
        when(twilio.whatsappConfigured()).thenReturn(true);
        Map<String, String> params = Map.of("RecordingUrl", "https://api.twilio.com/recordings/RE123", "From", "+919876543210");

        new VoiceService(configured(), twilio).voicemailComplete(params);

        verify(twilio).sendWhatsApp(eq("+911111111111"), anyString(), eq(null), eq(null));
        verify(twilio, never()).sendSms(anyString(), anyString());
    }

    @Test
    void voicemailCompleteDoesNothingWithoutARecording() {
        new VoiceService(configured(), twilio).voicemailComplete(Map.of("From", "+919876543210"));

        verify(twilio, never()).sendSms(anyString(), anyString());
        verify(twilio, never()).sendWhatsApp(anyString(), anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
