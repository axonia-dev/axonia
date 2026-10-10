package dev.axonia.communication.api.spi;

import java.util.Set;

import dev.axonia.communication.api.CommunicationPort;
import dev.axonia.communication.api.MessageType;

/**
 * Narrow read port {@link dev.axonia.communication.application.DeliveryGate} uses to judge a row's subject, without
 * {@code communication} ever learning what a subject is (nothing in this module may import from {@code events}).
 *
 * <p>
 * Declared here, in the module that needs the answer, and implemented by whichever module owns the kind of thing a
 * sender enqueued a message about - {@code events}' sessions, and {@code roundup}'s own organization - the same
 * dependency inversion {@code dev.axonia.organization.api.spi.OrganizationWriteGate} uses to keep the module graph
 * acyclic. {@code events} already depends on {@code communication} to call {@link CommunicationPort#enqueue}, so an
 * adapter living in {@code events} that implements this port adds no new edge; declaring the port the other way round -
 * {@code communication} importing an {@code events} read port - would create the cycle
 * {@code ApplicationModules.verify()} rejects.
 *
 * <p>
 * {@code subjectRef} is opaque here: it is a session id for one implementation and a quarter key for another, and
 * nothing in {@code communication} may know which, so both questions are phrased in terms {@code communication} already
 * understands - a {@link MessageType} and an id string - not in terms of sessions, communities or quarters.
 */
public interface MessageSubjectPolicy {

    /**
     * Which message types this implementation answers for. Claiming a type is what makes this edition send it: an open
     * type no implementation claims stops the application starting, and a type no implementation claims is suppressed
     * at delivery as unknown.
     */
    Set<MessageType> handles();

    /**
     * Whether {@code type} is enabled for the class of thing {@code subjectRef} belongs to. Which setting that is
     * belongs entirely to the implementing module: for a session message it is the per-message-type opt-out a community
     * sets in its communication settings, and for a roundup it is the organization's own roundup switch, the subject
     * there being the organization itself. Nothing here may assume a community, and a future sender answering for a
     * third kind of subject need not have one.
     */
    boolean isEnabledFor(String organizationId, MessageType type, String subjectRef);

    /**
     * Whether {@code userId} is still, right now, a member of whatever {@code subjectRef} belongs to - a community for
     * a session message, and for an organization-scoped subject, whether they are still present in its directory.
     */
    boolean isStillAMember(String organizationId, String subjectRef, String userId);
}
