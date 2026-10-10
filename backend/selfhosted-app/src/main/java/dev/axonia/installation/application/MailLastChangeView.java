package dev.axonia.installation.application;

import java.time.Instant;

public record MailLastChangeView(Instant occurredAt, String actorName) {
}
