package dev.axonia.expertise.api;

import java.time.Instant;

/** A member open to questions on an area, dated by their first declaration of it. */
public record OpenPerson(String userId, String displayName, String avatarUrl, Instant declaredAt) {
}
