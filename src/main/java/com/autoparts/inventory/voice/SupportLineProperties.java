package com.autoparts.inventory.voice;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * One published support number that rings every co-founder at once (a Twilio
 * "ring group"). Inert until {@link #enabled} is true and {@link #numbers} is set —
 * see {@link VoiceController}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.support-line")
public class SupportLineProperties {
    private boolean enabled = false;

    /** E.164 numbers to ring simultaneously, e.g. "+91XXXXXXXXXX". */
    private List<String> numbers = List.of();

    /** How long each number rings before Twilio gives up and moves to voicemail. */
    private int ringTimeoutSeconds = 20;

    private boolean voicemailEnabled = true;
    private int voicemailMaxLengthSeconds = 120;

    public boolean isConfigured() {
        return enabled && numbers != null && numbers.stream().anyMatch(n -> n != null && !n.isBlank());
    }
}
