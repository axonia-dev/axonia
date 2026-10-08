package dev.axonia.events;

/**
 * The outcome of assigning a presenter reference to a slot. A reference that does not resolve to exactly one member is
 * not an error: the slot is left as it was and the reason comes back, so a caller can ask a human rather than guess.
 *
 * <p>
 * When the reason is {@link #AMBIGUOUS}, {@code candidates} says how many members the reference matched, which is what
 * lets a caller tell two namesakes apart from a query that was simply too vague. It is a lower bound: the directory
 * stops counting once it has enough candidates to know the reference is ambiguous. It is 0 for every other outcome.
 */
public record PresenterAssignment(Session session, boolean assigned, String reason, int candidates) {

    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String AMBIGUOUS = "AMBIGUOUS";
}
