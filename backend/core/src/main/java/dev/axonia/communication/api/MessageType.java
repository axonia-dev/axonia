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

    private static final Map<String, MessageType> OPEN = open().stream()
            .collect(Collectors.toUnmodifiableMap(MessageType::code, Function.identity()));

    public MessageType {

        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Not a message type code: " + code);
        }
    }

    /** The types the open modules send, in the order a member's settings list them. */
    public static List<MessageType> open() {

        return List.of(SESSION_UPCOMING, SESSION_WRAP_UP, SESSION_AWAITING_CLOSE, ORGANIZATION_ROUNDUP);
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
