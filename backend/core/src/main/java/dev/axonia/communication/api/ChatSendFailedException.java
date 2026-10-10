package dev.axonia.communication.api;

/**
 * The cross-module shape of a post that did not arrive. {@code communication.application.port.out.ChatSendException}
 * extends this rather than duplicating it, so {@code community} - which sees only {@code communication.api} - still
 * catches the real failure by its {@link ChatFailureKind} and its message, not a rewritten copy of either.
 */
public class ChatSendFailedException extends RuntimeException {

    private final transient ChatFailureKind kind;

    public ChatSendFailedException(ChatFailureKind kind, String message) {

        super(message);
        this.kind = kind;
    }

    public ChatFailureKind kind() {

        return kind;
    }
}
