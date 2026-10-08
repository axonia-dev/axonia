package dev.axonia.communication.internal;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The batch handle a drain worker actually sends through: it buys a send slot before every message, and it opens the
 * connection underneath only once a message has one.
 *
 * <p>
 * <b>The slot is spent per send, never per row.</b> {@link OutboxRowProcessor#process} can suppress a row - opted out,
 * community switched off, no address - and return without touching the transport at all. Charged at the row instead, a
 * backlog of suppressed rows would spend the whole of the shared, replica-wide budget and put no mail on the wire.
 * Charging it here means the budget measures what it is named after: messages handed to the provider.
 *
 * <p>
 * <b>The connection is opened on the first message, not on the first row.</b> Opening it up front costs a full connect,
 * STARTTLS and AUTH for a slice that then sends nothing - a shutdown, a slice of suppressed rows, a tick whose budget
 * ran out while waiting. {@code CommunicationSweep.drainTick} walks organizations one at a time against one global send
 * budget, so that waste multiplies by the tenant count rather than being paid once.
 *
 * <p>
 * A connection that cannot be opened is raised as {@link MailAccountRefusedException} whatever went wrong underneath -
 * a refused credential, an unreachable host, a misconfigured transport factory. Nothing was sent either way, every row
 * behind this one would fail identically, and releasing them unmarked is the only answer that burns no attempt on any
 * of them.
 */
final class PacedMailBatch implements MailBatch {

    private final Supplier<MailBatch> connections;
    private final BooleanSupplier slots;
    private final Runnable accepted;

    private MailBatch connection;

    /**
     * {@code slots} answers false when the tick's send window has closed, never when the pace is merely spent.
     * {@code accepted} runs once for every message the connection took without refusing it.
     */
    PacedMailBatch(Supplier<MailBatch> connections, BooleanSupplier slots, Runnable accepted) {

        this.connections = connections;
        this.slots = slots;
        this.accepted = accepted;
    }

    @Override
    public void send(OutgoingMail mail) {

        if (!slots.getAsBoolean()) {
            throw new SendWindowClosedException("no send slot was free before this drain tick's deadline");
        }
        connection().send(mail);
        accepted.run();
    }

    /** Closes only what was opened: a batch nobody sent through holds nothing to close. */
    @Override
    public void close() {

        if (connection != null) {
            connection.close();
        }
    }

    private MailBatch connection() {

        if (connection == null) {
            try {
                connection = connections.get();
            } catch (RuntimeException e) {
                throw new MailAccountRefusedException("no mail connection could be opened for this worker's rows", e);
            }
        }
        return connection;
    }
}
