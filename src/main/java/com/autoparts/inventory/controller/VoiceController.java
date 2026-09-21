package com.autoparts.inventory.controller;

import com.autoparts.inventory.voice.TwilioSignatureValidator;
import com.autoparts.inventory.voice.VoiceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

/**
 * Twilio Voice webhooks for the support ring group (one published number, rings every
 * co-founder). Public and unauthenticated — Twilio can't send a Bearer token — so every
 * request is checked against {@link TwilioSignatureValidator} instead. Configure these
 * as the Twilio phone number's webhooks:
 * <pre>
 * A call comes in  -> POST {this app}/api/v1/voice/incoming
 * </pre>
 * (the other two are called by Twilio itself via the {@code action} URLs in the TwiML).
 */
@RestController
@RequestMapping("/api/v1/voice")
public class VoiceController {
    private static final String XML = "text/xml";

    private final VoiceService voiceService;
    private final TwilioSignatureValidator signatureValidator;

    public VoiceController(VoiceService voiceService, TwilioSignatureValidator signatureValidator) {
        this.voiceService = voiceService;
        this.signatureValidator = signatureValidator;
    }

    @PostMapping(value = "/incoming", produces = XML)
    public ResponseEntity<String> incoming(HttpServletRequest request) {
        return validated(request, () -> voiceService.incoming());
    }

    @PostMapping(value = "/no-answer", produces = XML)
    public ResponseEntity<String> noAnswer(HttpServletRequest request) {
        return validated(request, () -> voiceService.dialFinished(TwilioSignatureValidator.paramsOf(request)));
    }

    @PostMapping(value = "/voicemail-complete", produces = XML)
    public ResponseEntity<String> voicemailComplete(HttpServletRequest request) {
        return validated(request, () -> voiceService.voicemailComplete(TwilioSignatureValidator.paramsOf(request)));
    }

    private ResponseEntity<String> validated(HttpServletRequest request, Supplier<String> twiml) {
        if (!signatureValidator.isValid(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok().contentType(MediaType.valueOf(XML)).body(twiml.get());
    }
}
