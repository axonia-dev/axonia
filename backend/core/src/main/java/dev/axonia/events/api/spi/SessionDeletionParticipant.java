package dev.axonia.events.api.spi;

/**
 * Lets a module that keeps rows about a session let go of them when the session is deleted, inside the deletion's own
 * transaction, so a failure leaves the session and everything about it in place. Declared here and implemented by the
 * module holding those rows, the same inversion {@link SessionResourceAccess} uses to keep the module graph acyclic.
 */
public interface SessionDeletionParticipant {

    void sessionDeleted(String organizationId, long communityId, long sessionId);
}
