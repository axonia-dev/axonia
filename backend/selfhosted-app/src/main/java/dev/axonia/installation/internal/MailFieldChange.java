package dev.axonia.installation.internal;

/** One field of one change; {@code from} and {@code to} are always null for a secret. */
record MailFieldChange(String field, String kind, String from, String to) {
}
