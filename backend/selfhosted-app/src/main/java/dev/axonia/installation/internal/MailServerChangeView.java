package dev.axonia.installation.internal;

import java.time.Instant;
import java.util.List;

record MailServerChangeView(long id, Instant occurredAt, String actorName, List<MailFieldChange> changes) {

    MailServerChangeView {
        changes = List.copyOf(changes);
    }
}
