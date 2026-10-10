package dev.axonia.installation.application;

import java.time.Instant;

public record MailFailureView(String reason, Instant at) {
}
