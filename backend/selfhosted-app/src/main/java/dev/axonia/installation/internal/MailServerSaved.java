package dev.axonia.installation.internal;

/** What a save answers: the whole view to a caller who may read it, and only the revision it moved to otherwise. */
sealed interface MailServerSaved permits MailServerView, MailServerSaved.Revision {

    record Revision(long revision) implements MailServerSaved {
    }
}
