package dev.axonia.communication.application;

import java.util.Map;

/**
 * The tiny reads both {@code ChatPostBuilders} and {@link NotificationEntryBuilders} take from one message's frozen
 * facts, extracted once rather than kept as two copies that could drift: a blank-safe string, the same
 * {@code "This session"} fallback the mail shell's own {@code h1} uses ({@code $sessionTitle ?: 'This session'} in
 * {@code _shell.html}) when a session carries no title of its own, the same absent-not-a-stand-in sender name
 * {@code events}' {@code SessionFacts#sender} produces, and the one line that says when a session is. Existing once,
 * rather than once per platform mapper, is the point - the same argument {@link FactRehydration}'s own javadoc makes
 * for the module's other once-per-caller repair.
 *
 * <p>
 * {@link #when} is the one read here that carries a format, and it is here for exactly the reason the others are: two
 * mappers formatting the same instant separately is two chances to disagree about what DAY a session is on, and they
 * already did. Both surfaces reach a member's panel or channel, so a disagreement between them is a disagreement a
 * member sees.
 */
public final class FactStrings {

    private FactStrings() {
    }

    /**
     * When a session is, as a whole line - {@code null}, not a dangling "Held " with nothing after it, when the facts
     * carry no instant to render, which is the same absent-not-a-stand-in contract every other read here uses.
     *
     * <p>
     * The moment itself is {@link MomentLine}'s to render, shared with the mail templates: two surfaces formatting the
     * same instant separately is two chances to disagree about what DAY a session is on, and they already did once.
     * Both surfaces reach a member's panel or channel, so a disagreement between them is a disagreement a member sees.
     *
     * <p>
     * The compact form, which is the shape a chat card and the in-app panel have always had. Neither carries a
     * {@code readerTimeZone}: a chat post reaches a room rather than a person, so it renders on the session's own
     * clock.
     */
    public static String when(Map<String, Object> facts, String verb) {

        var moment = MomentLine.compact(facts);
        return moment == null ? null : verb + " " + moment;
    }

    /**
     * The same fallback the mail shell's own {@code h1} uses: a session routinely carries no title of its own, and
     * every platform mapper must still say the same thing the email a member already got says, rather than rendering a
     * blank heading.
     */
    public static String headline(Map<String, Object> facts) {

        var title = text(facts.get("sessionTitle"));
        return title == null ? "This session" : title;
    }

    /**
     * {@code null}, not a fallback name, when the community has none - the same absent state
     * {@code SessionFacts#sender} produces. Unlike {@code sessionTitle}, mail has no text fallback for this: the
     * shell's identity block ({@code th:if="${sender} != null"} in {@code _shell.html}) is omitted outright rather than
     * naming a stand-in sender, so each platform mapper does the same instead of rendering a blank identity line.
     */
    public static String senderName(Map<String, Object> facts) {

        return facts.get("sender") instanceof Map<?, ?> sender ? text(sender.get("name")) : null;
    }

    public static String text(Object value) {

        return value instanceof String string && !string.isBlank() ? string : null;
    }
}
