package dev.axonia.installation.internal;

import java.time.Instant;

record MailFailureView(String reason, Instant at) {
}
