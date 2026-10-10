package dev.axonia.installation.application;

import dev.axonia.communication.api.MailServerState;
import dev.axonia.installation.domain.MailServerRow;
import dev.axonia.installation.domain.MailValues;

/** The deployment's values, the stored ones (password opened), and the row they came from. */
public record ResolvedMailServer(MailValues overlay, MailValues stored, boolean passwordUnreadable, MailServerRow row) {

    public MailValues effective() {

        return overlay.over(stored);
    }

    public MailServerState state() {

        return passwordUnreadable ? MailServerState.notConfigured() : effective().state();
    }
}
