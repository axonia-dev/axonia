package dev.axonia.learning;

import java.time.Instant;

/** A current member who wants to learn an area, dated by the first spelling of it they stated. */
public record Learner(String userId, String displayName, String avatarUrl, Instant declaredAt) {
}
