package dev.axonia.installation.domain;

import java.time.Instant;

/** One recorded change of the mail settings; {@code changes} is the row's JSON as stored. */
public record SettingsChangeEntry(long id, Instant occurredAt, String actor, String changes) {
}
