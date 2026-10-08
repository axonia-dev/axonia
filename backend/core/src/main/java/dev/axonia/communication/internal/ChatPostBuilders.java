package dev.axonia.communication.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.axonia.communication.MessageType;

/**
 * Turns one message's frozen facts into a {@link ChatPost}. Reads exactly the keys {@code events}' {@code SessionFacts}
 * writes - not linkable from here, since {@code communication} never depends on {@code events} - and drops an absent
 * fact rather than rendering an empty row for it.
 */
@Component
class ChatPostBuilders {

    ChatPost build(MessageType type, Map<String, Object> facts) {

        if (MessageType.SESSION_UPCOMING.equals(type)) {
            return upcoming(facts);
        }
        if (MessageType.SESSION_WRAP_UP.equals(type)) {
            return wrapUp(facts);
        }
        // ChatDestinationsPort.channelTypes() claims only the two above, and JdbcOutbox writes a chat row for nothing
        // else; whoever widens channelTypes() adds the type here too.
        throw new IllegalArgumentException(type.code() + " does not post to a channel");
    }

    private ChatPost upcoming(Map<String, Object> facts) {

        var details = new LinkedHashMap<String, String>();
        putIfPresent(details, "Talks", talkLine(facts));
        putIfPresent(details, "Open slots", positiveCount(facts.get("openSlotCount")));
        return common(facts).whenLine(FactStrings.when(facts, "Happening")).details(details)
                .actionLabel("View the session").build();
    }

    private ChatPost wrapUp(Map<String, Object> facts) {

        var details = new LinkedHashMap<String, String>();
        putIfPresent(details, "Talks", talkLine(facts));
        putIfPresent(details, "Resources", resourceLine(facts));
        putIfPresent(details, "Recording", FactStrings.text(facts.get("recordingUrl")));
        putIfPresent(details, "Attended", positiveCount(facts.get("attendeeCount")));
        return common(facts).whenLine(FactStrings.when(facts, "Held"))
                .body(FactStrings.text(facts.get("followUpNotes"))).details(details).actionLabel("View the wrap-up")
                .build();
    }

    private ChatPost.ChatPostBuilder common(Map<String, Object> facts) {

        return ChatPost.builder().sender(FactStrings.senderName(facts)).headline(FactStrings.headline(facts))
                .actionUrl(FactStrings.text(facts.get("sessionUrl")));
    }

    /**
     * "Title, with Presenter" per talk, joined. A talk whose presenter did not resolve renders its title alone, the
     * same absent-state the mail templates already use rather than leaking an unresolved id.
     */
    private static String talkLine(Map<String, Object> facts) {

        if (!(facts.get("talks") instanceof List<?> talks)) {
            return null;
        }
        var line = talks.stream().filter(Map.class::isInstance).map(Map.class::cast).map(ChatPostBuilders::oneTalk)
                .filter(Objects::nonNull).collect(Collectors.joining("; "));
        return line.isEmpty() ? null : line;
    }

    private static String oneTalk(Map<?, ?> talk) {

        var title = FactStrings.text(talk.get("title"));
        if (title == null) {
            return null;
        }
        var presenter = FactStrings.text(talk.get("presenter"));
        return presenter == null ? title : title + ", with " + presenter;
    }

    /**
     * "Title: url" per session-level resource, joined - the same shape {@code talkLine} already uses for a list of its
     * own. Session-level only: {@code talks[].resources} stays out of the post by design, named in
     * {@code ChatPostMatchesMailTest}'s own deliberately-omitted set, because a flat chat card cannot carry per-talk
     * nesting without becoming unreadable.
     */
    private static String resourceLine(Map<String, Object> facts) {

        if (!(facts.get("resources") instanceof List<?> resources)) {
            return null;
        }
        var line = resources.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .map(ChatPostBuilders::oneResource).filter(Objects::nonNull).collect(Collectors.joining("; "));
        return line.isEmpty() ? null : line;
    }

    private static String oneResource(Map<?, ?> resource) {

        var url = FactStrings.text(resource.get("url"));
        if (url == null) {
            return null;
        }
        var title = FactStrings.text(resource.get("title"));
        return title == null ? url : title + ": " + url;
    }

    private static String positiveCount(Object value) {

        return value instanceof Number number && number.intValue() > 0 ? String.valueOf(number.intValue()) : null;
    }

    private static void putIfPresent(Map<String, String> details, String label, String value) {

        if (value != null) {
            details.put(label, value);
        }
    }
}
