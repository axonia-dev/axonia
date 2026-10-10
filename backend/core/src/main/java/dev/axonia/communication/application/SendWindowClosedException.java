package dev.axonia.communication.application;

/**
 * No send slot could be had before the drain tick's own deadline ran out, or before the context began shutting down.
 *
 * <p>
 * Not a failure of the message and not a refusal by the provider: nothing about this row was ever in question, and
 * nothing left the process. {@link DrainWorkers} answers it with {@code NOT_ATTEMPTED} - the row goes back to
 * {@code PENDING} unmarked, with no attempt burned - which is the same answer a shutdown between rows already gets, and
 * deliberately not the {@link MailAccountRefusedException} answer, which means the provider said stop.
 */
public class SendWindowClosedException extends RuntimeException {

    public SendWindowClosedException(String message) {
        super(message);
    }
}
