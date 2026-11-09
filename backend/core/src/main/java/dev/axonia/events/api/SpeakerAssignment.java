package dev.axonia.events.api;

/**
 * The outcome of naming a topic's speaker, with the same reasons as {@link PresenterAssignment}: a reference that does
 * not resolve to exactly one member leaves the topic as it was and says why.
 */
public record SpeakerAssignment(Topic topic, boolean assigned, String reason, int candidates) {

    public static final String NOT_FOUND = PresenterAssignment.NOT_FOUND;
    public static final String AMBIGUOUS = PresenterAssignment.AMBIGUOUS;
}
