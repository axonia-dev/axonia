package dev.axonia.communication.api.spi;

import dev.axonia.communication.api.DeliveryOutcome;
import dev.axonia.communication.api.MailServerState;

/**
 * The mail server delivery sends through. The Cloud answers it from its own properties; a Self-hosted installation from
 * what its Owner saved, under what its deployment overrides.
 */
public interface MailServer {

    /** Asked inside a row's transaction for every mail row, so it answers from memory and never queries. */
    MailServerState current();

    /**
     * Called on the drain's own thread after a batch, outside any transaction; a failure here is the adapter's. The
     * outcome may be about settings replaced since the batch connected, which {@link DeliveryOutcome#isAbout} tells.
     */
    void recordOutcome(DeliveryOutcome outcome);
}
