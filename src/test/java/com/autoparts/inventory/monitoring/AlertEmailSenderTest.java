package com.autoparts.inventory.monitoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertEmailSenderTest {
    @Mock ObjectProvider<JavaMailSender> mailProvider;
    @Mock JavaMailSender mailSender;

    private AlertEmailSender sender(MonitoringProperties props) {
        return new AlertEmailSender(mailProvider, props, "bot@gmail.com");
    }

    private static MonitoringProperties props(String alertTo, String alertFrom) {
        MonitoringProperties p = new MonitoringProperties();
        p.setAlertTo(alertTo);
        p.setAlertFrom(alertFrom);
        return p;
    }

    @Test
    void noOpWhenMailSenderMissing() {
        when(mailProvider.getIfAvailable()).thenReturn(null);

        assertFalse(sender(props("ops@x.com", null)).send("subj", "body"));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void noOpWhenNoRecipient() {
        when(mailProvider.getIfAvailable()).thenReturn(mailSender);

        assertFalse(sender(props(null, null)).send("subj", "body"));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendsToAllRecipientsFallingBackToMailUsernameForFrom() {
        when(mailProvider.getIfAvailable()).thenReturn(mailSender);
        org.mockito.ArgumentCaptor<SimpleMailMessage> msg = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);

        assertTrue(sender(props("a@x.com, b@x.com", null)).send("subj", "body"));

        verify(mailSender).send(msg.capture());
        assertArrayEquals(new String[]{"a@x.com", "b@x.com"}, msg.getValue().getTo());
        assertEquals("bot@gmail.com", msg.getValue().getFrom());
        assertEquals("subj", msg.getValue().getSubject());
    }

    @Test
    void sendFailureIsSwallowed() {
        when(mailProvider.getIfAvailable()).thenReturn(mailSender);
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("smtp down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertFalse(sender(props("ops@x.com", "from@x.com")).send("subj", "body"));
    }
}
