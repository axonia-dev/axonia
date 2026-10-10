package dev.axonia.installation.domain;

/** One field of one change; {@code from} and {@code to} are always null for a secret. */
public record MailFieldChange(String field, String kind, String from, String to) {
}
