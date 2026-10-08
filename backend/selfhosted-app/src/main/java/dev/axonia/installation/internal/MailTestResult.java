package dev.axonia.installation.internal;

import java.time.Instant;

import lombok.Builder;

@Builder
record MailTestResult(boolean accepted, String host, Instant acceptedAt, String testToken, Instant expiresAt) {
}
