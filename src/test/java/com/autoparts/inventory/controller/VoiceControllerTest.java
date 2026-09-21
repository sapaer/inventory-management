package com.autoparts.inventory.controller;

import com.autoparts.inventory.voice.TwilioSignatureValidator;
import com.autoparts.inventory.voice.VoiceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoiceControllerTest {
    @Mock VoiceService voiceService;
    @Mock TwilioSignatureValidator signatureValidator;

    private VoiceController controller() {
        return new VoiceController(voiceService, signatureValidator);
    }

    @Test
    void unsignedRequestIsRejected() {
        when(signatureValidator.isValid(any())).thenReturn(false);

        ResponseEntity<String> res = controller().incoming(new MockHttpServletRequest("POST", "/api/v1/voice/incoming"));

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
        verify(voiceService, never()).incoming();
    }

    @Test
    void signedRequestReturnsTwiml() {
        when(signatureValidator.isValid(any())).thenReturn(true);
        when(voiceService.incoming()).thenReturn("<Response><Dial/></Response>");

        ResponseEntity<String> res = controller().incoming(new MockHttpServletRequest("POST", "/api/v1/voice/incoming"));

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("text/xml", res.getHeaders().getContentType().toString().split(";")[0]);
        assertEquals("<Response><Dial/></Response>", res.getBody());
    }
}
