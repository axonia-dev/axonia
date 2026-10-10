package dev.axonia.communication.application.port.out;

import dev.axonia.communication.application.MailAccountRefusedException;
import dev.axonia.communication.application.MailHold;
import dev.axonia.communication.application.OutboxDrain;

/** What {@link OutboxDrain} sends rendered, addressed messages through. {@code SmtpDelivery} is the one adapter. */
public interface MailDelivery {

    /**
     * Opens a conversation the caller sends through and then closes. Throws unchecked if it cannot connect, and always
     * as a {@link MailAccountRefusedException}: a connection nobody can open is about the account or the provider, not
     * about any one message, so the caller holds the mail (see {@link MailHold}) and releases its mail rows unmarked
     * instead of burning an attempt on each in turn, while its other rows go on.
     *
     * <p>
     * {@link MailBatch#send} throws unchecked on a per-message transport failure; the caller marks that row and moves
     * on to the next one - except, again, for {@link MailAccountRefusedException}, on which the caller holds the mail
     * (see {@link MailHold}), since every mail row behind it would be refused identically.
     */
    MailBatch open();

    /** Hands the mail server how the batches opened since the last call went, if anything was sent or refused. */
    void reportOutcome();
}
