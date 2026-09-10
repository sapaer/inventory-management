package com.autoparts.inventory.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends monitoring alert emails via whatever SMTP Spring Mail is configured with
 * (Gmail in prod). If mail is not configured or no recipient is set, {@link #send}
 * is a no-op returning {@code false} — the caller still logs the alert at WARN.
 */
@Component
public class AlertEmailSender {
    private static final Logger log = LoggerFactory.getLogger(AlertEmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final MonitoringProperties props;
    private final String mailUsername;

    public AlertEmailSender(
            ObjectProvider<JavaMailSender> mailSender,
            MonitoringProperties props,
            @Value("${spring.mail.username:}") String mailUsername
    ) {
        this.mailSender = mailSender;
        this.props = props;
        this.mailUsername = mailUsername;
    }

    public boolean send(String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        String to = props.getAlertTo();
        if (sender == null || to == null || to.isBlank()) {
            log.warn("alert email skipped (mail host or MONITORING_ALERT_TO not configured): {}", subject);
            return false;
        }
        String from = props.getAlertFrom() == null || props.getAlertFrom().isBlank()
                ? mailUsername
                : props.getAlertFrom();
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (from != null && !from.isBlank()) {
                message.setFrom(from);
            }
            message.setTo(splitRecipients(to));
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            log.info("alert email sent to={} subject={}", to, subject);
            return true;
        } catch (Exception ex) {
            log.error("alert email send failed subject={}", subject, ex);
            return false;
        }
    }

    private static String[] splitRecipients(String csv) {
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }
}
