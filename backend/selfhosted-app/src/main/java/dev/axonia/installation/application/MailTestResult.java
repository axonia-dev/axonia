package dev.axonia.installation.application;

import java.time.Instant;

import lombok.Builder;

@Builder
public record MailTestResult(boolean accepted, String host, Instant acceptedAt, String testToken, Instant expiresAt) {
}
