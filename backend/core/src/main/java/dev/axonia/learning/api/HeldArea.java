package dev.axonia.learning.api;

import java.time.Instant;
import java.util.List;

/**
 * An area the caller wants to learn, named as the community reads it, dated by the first spelling they stated, with the
 * other members open to questions on it.
 */
public record HeldArea(String tag, Instant declaredAt, List<OpenFace> open) {
}
