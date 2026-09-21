package com.autoparts.inventory.voice;

import com.autoparts.inventory.client.TwilioMessagingClient;
import com.twilio.http.HttpMethod;
import com.twilio.twiml.TwiMLException;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Dial;
import com.twilio.twiml.voice.Hangup;
import com.twilio.twiml.voice.Number;
import com.twilio.twiml.voice.Record;
import com.twilio.twiml.voice.Say;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Builds the TwiML for the support ring group: one Twilio number that dials every
 * co-founder in {@link SupportLineProperties#getNumbers()} simultaneously, falls back
 * to a recorded voicemail if nobody picks up, and notifies the founders of it.
 */
@Service
public class VoiceService {
    private static final Logger log = LoggerFactory.getLogger(VoiceService.class);

    private final SupportLineProperties props;
    private final TwilioMessagingClient twilio;

    public VoiceService(SupportLineProperties props, TwilioMessagingClient twilio) {
        this.props = props;
        this.twilio = twilio;
    }

    /** Twilio's "A call comes in" webhook: ring every configured number at once. */
    public String incoming() {
        if (!props.isConfigured()) {
            log.warn("support line called but app.support-line.enabled/numbers are not set");
            return sayAndHangup("Sorry, this line is not set up yet. Please try again later.");
        }
        Dial.Builder dial = new Dial.Builder()
                .timeout(props.getRingTimeoutSeconds())
                .method(HttpMethod.POST)
                .action("/api/v1/voice/no-answer");
        for (String number : props.getNumbers()) {
            if (number != null && !number.isBlank()) {
                // Nest each as its own <Number> child (not the string-body overload,
                // which sets a single-number <Dial> and can only hold one).
                dial.number(new Number.Builder(number.trim()).build());
            }
        }
        return toXml(new VoiceResponse.Builder().dial(dial.build()).build());
    }

    /** Called after the dial attempt ends, answered or not. */
    public String dialFinished(Map<String, String> params) {
        String status = params.get("DialCallStatus");
        if ("completed".equals(status)) {
            return toXml(new VoiceResponse.Builder().hangup(new Hangup.Builder().build()).build());
        }
        log.info("support call unanswered status={} from={}", status, params.get("From"));
        if (!props.isVoicemailEnabled()) {
            return sayAndHangup("Sorry, no one is available to take your call right now. Please try again later.");
        }
        Record record = new Record.Builder()
                .maxLength(props.getVoicemailMaxLengthSeconds())
                .playBeep(true)
                .method(HttpMethod.POST)
                .action("/api/v1/voice/voicemail-complete")
                .build();
        return toXml(new VoiceResponse.Builder()
                .say(new Say.Builder(
                        "Sorry, we're unable to take your call right now. "
                                + "Please leave your name and a brief message after the beep.").build())
                .record(record)
                .say(new Say.Builder("We did not receive a message. Goodbye.").build())
                .build());
    }

    /** Called once the voicemail recording finishes; notifies every founder. */
    public String voicemailComplete(Map<String, String> params) {
        String recordingUrl = params.get("RecordingUrl");
        if (recordingUrl != null && !recordingUrl.isBlank()) {
            notifyFounders(params.get("From"), recordingUrl, params.get("RecordingDuration"));
        }
        return sayAndHangup("Thank you. Goodbye.");
    }

    private void notifyFounders(String from, String recordingUrl, String durationSeconds) {
        String caller = (from == null || from.isBlank()) ? "an unknown number" : from;
        String duration = (durationSeconds == null || durationSeconds.isBlank()) ? "" : " (" + durationSeconds + "s)";
        String message = "New support line voicemail from " + caller + duration + ". Recording: " + recordingUrl;
        for (String number : props.getNumbers()) {
            if (number == null || number.isBlank()) {
                continue;
            }
            try {
                if (twilio.smsConfigured()) {
                    twilio.sendSms(number, message);
                } else if (twilio.whatsappConfigured()) {
                    // Best-effort: WhatsApp business-initiated freeform messages only land
                    // inside a 24h window after the recipient last messaged us.
                    twilio.sendWhatsApp(number, message, null, null);
                } else {
                    log.warn("voicemail left but no SMS/WhatsApp channel configured to notify founders");
                }
            } catch (Exception ex) {
                log.error("voicemail notification failed to={}", number, ex);
            }
        }
    }

    private String sayAndHangup(String text) {
        return toXml(new VoiceResponse.Builder()
                .say(new Say.Builder(text).build())
                .hangup(new Hangup.Builder().build())
                .build());
    }

    private static String toXml(VoiceResponse response) {
        try {
            return response.toXml();
        } catch (TwiMLException ex) {
            throw new IllegalStateException("could not build TwiML response", ex);
        }
    }
}
