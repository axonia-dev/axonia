package dev.axonia.mentoring.domain;

import java.util.List;
import java.util.Locale;

import dev.axonia.shared.api.error.ValidationException;

/** What a proposal must carry: one to five short topics and a short message. */
public final class ProposalRules {

    public static final int MAX_TOPICS = 5;

    public static final int MAX_TOPIC_LENGTH = 80;

    public static final int MAX_MESSAGE_LENGTH = 500;

    private static final String TOO_LONG = "MENTORING_TOO_LONG";

    private static final String INVALID = "field_invalid";

    private ProposalRules() {
    }

    /** The topics trimmed, in the proposer's order, each once whatever its case. */
    public static List<String> topics(List<String> raw) {

        if (raw == null || raw.isEmpty() || raw.size() > MAX_TOPICS) {
            throw new ValidationException(INVALID, "A proposal names one to " + MAX_TOPICS + " topics");
        }
        var topics = raw.stream().map(ProposalRules::topic).toList();
        if (topics.stream().map(t -> t.toLowerCase(Locale.ROOT)).distinct().count() < topics.size()) {
            throw new ValidationException(INVALID, "A proposal names each topic once");
        }
        return topics;
    }

    public static void requireAnotherPerson(String me, String to) {

        if (to == null || to.isBlank()) {
            throw new ValidationException(INVALID, "A proposal names who it goes to");
        }
        if (me.equals(to)) {
            throw new ValidationException("MENTORING_SELF", "A member cannot propose mentoring to themselves");
        }
    }

    public static String message(String raw) {

        var message = raw == null ? "" : raw.strip();
        if (message.isEmpty()) {
            throw new ValidationException(INVALID, "A proposal carries a message");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new ValidationException(TOO_LONG, "A message is at most " + MAX_MESSAGE_LENGTH + " characters");
        }
        return message;
    }

    /** A topic's name, trimmed; a journal names its topics by the same rule a proposal does. */
    public static String topic(String raw) {

        var topic = raw == null ? "" : raw.strip();
        if (topic.isEmpty()) {
            throw new ValidationException(INVALID, "A topic needs a name");
        }
        if (topic.length() > MAX_TOPIC_LENGTH) {
            throw new ValidationException(TOO_LONG, "A topic is at most " + MAX_TOPIC_LENGTH + " characters");
        }
        return topic;
    }
}
