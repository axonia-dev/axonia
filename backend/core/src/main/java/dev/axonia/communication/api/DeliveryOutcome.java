package dev.axonia.communication.api;

import java.util.Objects;

/**
 * How a mail server answered delivery, and the settings delivery used: the fingerprint of the values it connected with,
 * so an answer about settings replaced since is never taken for one about the current settings.
 */
public record DeliveryOutcome(String settingsFingerprint, MailOutcome outcome) {

    public DeliveryOutcome {
        Objects.requireNonNull(settingsFingerprint, "settingsFingerprint");
        Objects.requireNonNull(outcome, "outcome");
    }

    public static DeliveryOutcome of(MailServerSettings settings, MailOutcome outcome) {

        return new DeliveryOutcome(settings.fingerprint(), outcome);
    }

    public boolean isAbout(MailServerSettings settings) {

        return settingsFingerprint.equals(settings.fingerprint());
    }

    /** Leaves the fingerprint out: it is an unkeyed hash over the password, so a logged one could test guesses. */
    @Override
    public String toString() {

        return "DeliveryOutcome[outcome=" + outcome + "]";
    }
}
