package dev.axonia.communication.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import dev.axonia.communication.api.spi.MessageSubjectPolicy;

/**
 * A kind of message, by the stable code it travels and persists as (e.g. {@code "session_wrap_up"}), never a Java
 * constant name. The open modules' types are the constants here; a module of another edition sends a type of its own by
 * declaring it with {@code MessageType.of} and claiming it in a {@link MessageSubjectPolicy}, which is what makes an
 * edition send it at all.
 */
public record MessageType(@JsonValue String code) {

    private static final Pattern CODE = Pattern.compile("[a-z][a-z_]{0,31}");

    public static final MessageType SESSION_UPCOMING = new MessageType("session_upcoming");

    public static final MessageType SESSION_WRAP_UP = new MessageType("session_wrap_up");

    public static final MessageType SESSION_AWAITING_CLOSE = new MessageType("session_awaiting_close");

    public static final MessageType ORGANIZATION_ROUNDUP = new MessageType("organization_roundup");

    /** In-app only: {@code learning} tells a learner that someone is now open to questions on an area they hold. */
    public static final MessageType OPEN_TO_QUESTIONS = new MessageType("open_to_questions");

    /** Mailed: {@code mentoring} tells a member someone proposes mentoring with them. */
    public static final MessageType MENTORING_PROPOSAL = new MessageType("mentoring_proposal");

    /** In-app only: {@code mentoring} tells a member their proposal was answered "not this time". */
    public static final MessageType MENTORING_DECLINED = new MessageType("mentoring_declined");

    /** In-app only: {@code mentoring} tells one side of a pairing that it ended. */
    public static final MessageType MENTORING_ENDED = new MessageType("mentoring_ended");

    /**
     * In-app only: {@code mentoring} told a mentee their mentor asks for a few words. Never sent again, still declared
     * so the entries already in a panel render and erasure finds them.
     */
    public static final MessageType MENTORING_WORDS_ASKED = new MessageType("mentoring_words_asked");

    /** In-app only: {@code mentoring} tells a mentor that words from their mentee arrived. */
    public static final MessageType MENTORING_WORDS_RECEIVED = new MessageType("mentoring_words_received");

    /** In-app only: {@code mentoring} tells one side of a pairing the other set or changed the next meeting. */
    public static final MessageType MENTORING_MEETING_SET = new MessageType("mentoring_meeting_set");

    /** Mailed: {@code mentoring} reminds each side of a pairing of its next meeting, the day before. */
    public static final MessageType MENTORING_MEETING_REMINDER = new MessageType("mentoring_meeting_reminder");

    /** In-app only: {@code mentoring} tells a member the person who declined their proposal reopened it. */
    public static final MessageType MENTORING_PROPOSAL_REOPENED = new MessageType("mentoring_proposal_reopened");

    /** Mailed: {@code mentoring} asks once whether a mentor an ended, never shown pairing would list is shown. */
    public static final MessageType MENTORING_LISTING_ASK = new MessageType("mentoring_listing_ask");

    /** In-app only: {@code mentoring} tells a mentor once that they are listed among the community's mentors. */
    public static final MessageType MENTORING_LISTED = new MessageType("mentoring_listed");

    private static final Map<String, MessageType> OPEN = open().stream()
            .collect(Collectors.toUnmodifiableMap(MessageType::code, Function.identity()));

    public MessageType {

        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Not a message type code: " + code);
        }
    }

    /** The types the open modules send, in the order a member's settings list them. */
    public static List<MessageType> open() {

        return List.of(SESSION_UPCOMING, SESSION_WRAP_UP, SESSION_AWAITING_CLOSE, ORGANIZATION_ROUNDUP,
                OPEN_TO_QUESTIONS, MENTORING_PROPOSAL, MENTORING_DECLINED, MENTORING_ENDED, MENTORING_WORDS_ASKED,
                MENTORING_WORDS_RECEIVED, MENTORING_MEETING_SET, MENTORING_MEETING_REMINDER,
                MENTORING_PROPOSAL_REOPENED, MENTORING_LISTING_ASK, MENTORING_LISTED);
    }

    /** The type a code names: an open code's constant itself, so an open type read back keeps its identity. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static MessageType of(String code) {

        var open = code == null ? null : OPEN.get(code);
        return open == null ? new MessageType(code) : open;
    }

    /** The type a stored or signed code names, as {@link #of} does, or empty when it is not a code at all. */
    public static Optional<MessageType> parse(String code) {

        return code != null && CODE.matcher(code).matches() ? Optional.of(of(code)) : Optional.empty();
    }
}
