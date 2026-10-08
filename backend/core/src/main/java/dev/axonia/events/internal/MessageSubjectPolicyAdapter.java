package dev.axonia.events.internal;

import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.NotFoundException;
import dev.axonia.communication.MessageSubjectPolicy;
import dev.axonia.communication.MessageType;
import dev.axonia.events.CommunityAccess;
import lombok.RequiredArgsConstructor;

/**
 * Adapter that satisfies the {@link MessageSubjectPolicy} port (declared in the {@code communication} module) by
 * resolving a row's opaque {@code subjectRef} to a session, then to that session's community.
 *
 * <p>
 * Lives in {@code events.internal} - {@code events} is the module that owns sessions, and will already depend on
 * {@code communication} to call {@code CommunicationPort.enqueue}, so an adapter here adds no new inter-module edge
 * that {@code communication} does not already accept on its own terms. Declaring this port the other way round -
 * {@code communication} importing an {@code events} read port - would create a cycle Spring Modulith's
 * {@code ApplicationModules.verify()} rejects, since {@code events} depends on {@code communication} for enqueueing.
 *
 * <p>
 * Both methods here call the org-explicit {@link CommunityAccess} overloads, never the implicit-organization ones: this
 * adapter is reached from {@code DeliveryGate}, which runs inside the communication drain's cross-tenant sweep (each
 * organization's unit wrapped in {@code OrganizationScope.runAs}, which installs a JWT-less authentication). The
 * implicit-organization overloads read an {@code OrganizationContext} that resolves the organization from the caller's
 * JWT, which does not exist there - calling them would throw {@code IllegalStateException} on every row.
 *
 * <p>
 * A row whose subject cannot be resolved at all - an unparseable {@code subjectRef}, a deleted session, or a deleted
 * community - is treated as fail-closed (not enabled, not a member) rather than propagating an exception into the drain
 * loop, the same policy {@code bookmarks.internal.CommunityAccessAdapter} and
 * {@code recognition.internal.CommunityAccessAdapter} already apply to a community that no longer exists.
 */
@Component
@RequiredArgsConstructor
class MessageSubjectPolicyAdapter implements MessageSubjectPolicy {

    private static final Set<MessageType> HANDLED = Set.of(MessageType.SESSION_UPCOMING, MessageType.SESSION_WRAP_UP,
            MessageType.SESSION_AWAITING_CLOSE);

    private final SessionSubjectResolver sessionSubjectResolver;
    private final CommunityAccess communityAccess;

    @Override
    public Set<MessageType> handles() {

        return HANDLED;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEnabledFor(String organizationId, MessageType type, String subjectRef) {

        return sessionSubjectResolver.communityIdFor(organizationId, subjectRef)
                .map(communityId -> classEnabled(type, organizationId, communityId)).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isStillAMember(String organizationId, String subjectRef, String userId) {

        return sessionSubjectResolver.communityIdFor(organizationId, subjectRef)
                .map(communityId -> communityAccess.isMember(organizationId, communityId, userId)).orElse(false);
    }

    private boolean classEnabled(MessageType type, String organizationId, long communityId) {

        try {
            var settings = communityAccess.communicationSettings(organizationId, communityId);
            if (MessageType.SESSION_UPCOMING.equals(type)) {
                return settings.notifySessionUpcoming();
            }
            if (MessageType.SESSION_WRAP_UP.equals(type)) {
                return settings.notifySessionWrapUp();
            }
            // No community setting opts a member's community out of the seven-day organizer nudge; a recipient can
            // still turn it off individually through DeliveryGate's per-member preference check, which runs after this.
            return MessageType.SESSION_AWAITING_CLOSE.equals(type);
        } catch (NotFoundException communityNoLongerExists) {
            return false;
        }
    }
}
