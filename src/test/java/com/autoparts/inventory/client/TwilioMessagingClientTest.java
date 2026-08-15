package com.autoparts.inventory.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TwilioMessagingClientTest {
    @Test
    void formatsIndianMobileToE164AndWhatsApp() {
        assertEquals("+918619544044", TwilioMessagingClient.toE164("8619544044"));
        assertEquals("+918619544044", TwilioMessagingClient.toE164("+918619544044"));
        assertEquals("whatsapp:+918619544044", TwilioMessagingClient.toWhatsApp("8619544044"));
        assertEquals("whatsapp:+14155238886", TwilioMessagingClient.toWhatsAppFrom("+14155238886"));
        assertEquals("whatsapp:+14155238886", TwilioMessagingClient.toWhatsAppFrom("whatsapp:+14155238886"));
    }
}
