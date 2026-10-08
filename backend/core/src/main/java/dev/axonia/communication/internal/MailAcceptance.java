package dev.axonia.communication.internal;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Whether the mail server accepted any message of one batch: the only state of a batch its workers write and the drain
 * reads. Each worker reports on its own virtual thread; the drain reads the answer once {@link DrainWorkers#runAll} has
 * returned, so every report is in by then.
 */
final class MailAcceptance {

    private final AtomicBoolean any = new AtomicBoolean();

    void accepted() {

        any.set(true);
    }

    boolean anyAccepted() {

        return any.get();
    }
}
