package dev.axonia.communication.application;

/**
 * Whether one organization's drain has stopped claiming mail for the rest of its tick. Every way the mail server
 * refuses - a refused connection, a refused sender, an account limit, a connection dropped mid-batch - reaches the
 * drain as {@link MailAccountRefusedException}, and the worker that met it holds the mail of its own slice for the rest
 * of the batch. The drain holds the mail for the rest of the tick only when a batch met a refusal and no worker had a
 * single message accepted ({@link MailAcceptance}): a server that refuses one connection of several, past its cap on
 * concurrent connections, still takes the mail of the others. From then on its claims leave mail where it is while its
 * in-app entries and chat posts go on in order, and the next tick starts with a fresh hold and tries the mail again. It
 * also remembers whether the tick has met a refusal before, so the drain says so once per tick rather than once per
 * refused batch.
 *
 * <p>
 * It belongs to one {@link OutboxDrain#drain} call and never leaves its thread: the workers see only the batch's
 * {@link MailAcceptance}.
 */
public final class MailHold {

    private boolean held;
    private boolean refusedBefore;
    private boolean firstRefusal;

    void endBatch(boolean refused, boolean accepted) {

        held = held || refused && !accepted;
        firstRefusal = refused && !refusedBefore;
        refusedBefore = refusedBefore || refused;
    }

    boolean isHeld() {

        return held;
    }

    /** Whether the batch last ended was the first of this tick to meet a refusal. */
    boolean firstRefusal() {

        return firstRefusal;
    }
}
