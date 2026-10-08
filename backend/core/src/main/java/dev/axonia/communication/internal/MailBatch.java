package dev.axonia.communication.internal;

/**
 * An open conversation with the mail transport that several messages leave through before it is closed.
 *
 * <p>
 * The connection, its TLS negotiation and its authentication are the dominant cost of sending one message to a remote
 * provider, and {@code JavaMailSenderImpl.send(MimeMessage)} pays all three every time. A batch pays them once.
 *
 * <p>
 * Deliberately not {@code send(MimeMessage[])}, which would send the whole array in one call: the outbox writes each
 * row's {@code markSent} before that row's own send, so that a failure loses at most the one message genuinely in
 * flight. Handing an array to the transport would force the marking to be batched too and widen the duplicate-send
 * window from one message to a whole batch, undoing what {@link OutboxRowProcessor}'s per-row transaction exists for.
 */
interface MailBatch extends AutoCloseable {

    void send(OutgoingMail mail);

    @Override
    void close();
}
