package dev.axonia.communication;

import java.util.List;

/**
 * What the drain needs to know about a recipient, asked of whichever module owns people.
 *
 * <p>
 * Declared here rather than imported from {@code identity}, which is the direction {@link ChatDestinationsPort} and
 * {@link MessageSubjectPolicy} are already declared in. This module importing another module's domain is what made
 * {@code identity} unable to enqueue anything at all, since {@code ApplicationModules.verify()} refuses the resulting
 * cycle, and an SSO credential warning has to be produced by the module that owns SSO connections.
 *
 * <p>
 * {@link Presence} is this module's own vocabulary rather than a re-export, so the port states what the drain actually
 * distinguishes: somebody who can be written to, somebody who has left, and somebody whose row is gone. The adapter
 * maps its own model onto it.
 */
public interface RecipientDirectory {

    /**
     * Total by construction: one result per requested id, always, so a recipient the directory cannot see is
     * {@link Presence#ERASED} rather than a missing entry the caller has to notice.
     */
    List<Recipient> recipientsOf(String organizationId, List<String> userIds);

    enum Presence {
        PRESENT, DEACTIVATED, ERASED
    }

    /**
     * {@code timeZone} is the recipient's own clock, or null when it is unknown. Null is a real answer here rather than
     * an omission: a message renders on the session's own clock for a recipient nobody has measured, which is still
     * better than the UTC every session message stated before.
     */
    record Recipient(String userId, String displayName, String email, Presence presence, String timeZone) {
    }
}
