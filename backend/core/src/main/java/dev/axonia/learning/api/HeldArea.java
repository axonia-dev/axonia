package dev.axonia.learning.api;

import java.time.Instant;
import java.util.List;

/**
 * An area the caller wants to learn, named as the community reads it, dated by the first spelling they stated, with how
 * many other members are open to questions on it and the first {@link #MAX_FACES} of them.
 */
public record HeldArea(String tag, Instant declaredAt, int openCount, List<OpenFace> open) {

    /** As many faces as a learning row shows; the count says how many more there are. */
    public static final int MAX_FACES = 4;
}
