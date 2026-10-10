package dev.axonia.installation.application;

import java.time.Instant;
import java.util.List;

import dev.axonia.installation.domain.MailFieldChange;

public record MailServerChangeView(long id, Instant occurredAt, String actorName, List<MailFieldChange> changes) {

    public MailServerChangeView {
        changes = List.copyOf(changes);
    }
}
