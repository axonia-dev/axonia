package dev.axonia.communication;

import java.util.Objects;

public sealed interface MailServerState {

    static MailServerState configured(MailServerSettings settings) {

        return new Configured(settings);
    }

    static MailServerState notConfigured() {

        return new NotConfigured();
    }

    record Configured(MailServerSettings settings) implements MailServerState {

        public Configured {
            Objects.requireNonNull(settings, "settings");
        }
    }

    /**
     * Every mail row drained while this is the answer is suppressed for good and counted as could not be reached, so an
     * adapter answers it only when no server is meant to be configured, or whose stored credential can no longer be
     * read, which the Self-hosted edition suppresses on purpose ({@code secret_unreadable}); never for a server it
     * cannot reach.
     */
    record NotConfigured() implements MailServerState {
    }
}
