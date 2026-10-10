package dev.axonia.events.domain;

import java.util.Arrays;
import java.util.Optional;

import dev.axonia.communication.api.MessageType;
import dev.axonia.shared.api.error.ValidationException;

/**
 * The two member-facing messages a session sends because an organizer decided it should: the reminder before it, and
 * the wrap-up after it. Each pairs its {@link MessageType} with the Thymeleaf template basename it renders through -
 * {@code MailRenderer} resolves that to {@code templates/mail/<name>.html} and {@code .txt} - so the producer that
 * mails it and {@code SessionMessagePreviewer} that shows an organizer what it will look like read ONE constant rather
 * than each holding its own copy of the literal. A mismatch between the two would mean a preview silently rendering a
 * DIFFERENT template than the one a real send uses, the loudest possible version of the divergence a preview exists to
 * rule out.
 *
 * <p>
 * {@code SESSION_AWAITING_CLOSE} is deliberately not one of them. It is an organizer-only nudge that stays automatic:
 * no stored release intent, no release card, and so nothing an organizer could preview or control.
 */
public enum ReleasableMessage {

    UPCOMING(MessageType.SESSION_UPCOMING, "session-upcoming"), WRAP_UP(MessageType.SESSION_WRAP_UP, "session-wrap-up");

    private final MessageType type;
    private final String template;

    ReleasableMessage(MessageType type, String template) {

        this.type = type;
        this.template = template;
    }

    public MessageType type() {

        return type;
    }

    public String template() {

        return template;
    }

    /**
     * Resolves the wire code an HTTP caller names - {@link MessageType#code}, e.g. {@code "session_wrap_up"} - to the
     * one message it means, and refuses everything else. Deliberately the single refusal point for both ways a caller
     * can get this wrong: a code naming no message type at all, and a code naming a real one that no organizer
     * releases. Splitting them would mean two answers to the same question, and a default - "unrecognised, so render
     * the wrap-up" - would be worse than either, because it passes every happy-path test while showing somebody the
     * wrong message under a URL that claims otherwise.
     *
     * <p>
     * It composes over {@link MessageType#parse} rather than scanning the codes itself, so this class never holds a
     * second opinion about what the wire vocabulary is: {@code communication} owns code-to-type, and {@code events}
     * only narrows that to the types an organizer releases. A code {@code communication} stops recognising therefore
     * stops resolving here too, instead of the two lookups quietly disagreeing.
     */
    public static ReleasableMessage of(String code) {

        return MessageType.parse(code).flatMap(ReleasableMessage::forType)
                .orElseThrow(() -> new ValidationException("UNSUPPORTED_MESSAGE_TYPE",
                        "Not a session message an organizer releases: " + code));
    }

    private static Optional<ReleasableMessage> forType(MessageType type) {

        return Arrays.stream(values()).filter(message -> message.type.equals(type)).findFirst();
    }
}
