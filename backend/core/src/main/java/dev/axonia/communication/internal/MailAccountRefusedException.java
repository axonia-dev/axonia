package dev.axonia.communication.internal;

/**
 * The transport refused for a reason that is about the account rather than about this message: a quota exhausted, a
 * credential rejected, the provider's own rate limit hit. Every row behind it will be refused identically, so it must
 * never be recorded against the one message that happened to be in flight.
 *
 * <p>
 * That distinction is load-bearing. Treated as an ordinary send failure, a quota rejection increments {@code attempts}
 * on every row it touches and, past {@code maxAttempts}, marks each {@code ABANDONED} - terminal, never reclaimed. A
 * provider's daily cap would therefore destroy the remainder of a queue rather than delay it, silently, with nothing an
 * operator could do to recover it. The drain answers it by holding mail instead (see {@link MailHold}): the refused
 * mail rows go back unmarked and wait to be claimed again, the in-app entries and chat posts go on.
 */
class MailAccountRefusedException extends RuntimeException {

    MailAccountRefusedException(String message, Throwable cause) {
        super(message, cause);
    }
}
