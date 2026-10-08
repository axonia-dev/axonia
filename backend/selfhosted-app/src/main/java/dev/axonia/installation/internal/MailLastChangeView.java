package dev.axonia.installation.internal;

import java.time.Instant;

record MailLastChangeView(Instant occurredAt, String actorName) {
}
