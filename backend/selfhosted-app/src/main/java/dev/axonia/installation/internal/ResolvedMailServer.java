package dev.axonia.installation.internal;

import dev.axonia.communication.MailServerState;

/** The deployment's values, the stored ones (password opened), and the row they came from. */
record ResolvedMailServer(MailValues overlay, MailValues stored, boolean passwordUnreadable, MailServerRow row) {

    MailValues effective() {

        return overlay.over(stored);
    }

    MailServerState state() {

        return passwordUnreadable ? MailServerState.notConfigured() : effective().state();
    }
}
