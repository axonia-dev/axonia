package dev.axonia.learning.api;

import java.time.Instant;

/** An area the caller wants to learn, named as the community reads it, dated by the first spelling they stated. */
public record HeldArea(String tag, Instant declaredAt) {
}
