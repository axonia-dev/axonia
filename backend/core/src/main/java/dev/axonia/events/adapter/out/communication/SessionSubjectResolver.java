package dev.axonia.events.adapter.out.communication;

import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.axonia.events.adapter.out.persistence.SessionEntity;
import dev.axonia.events.adapter.out.persistence.SessionRepository;
import lombok.RequiredArgsConstructor;

/**
 * Walks a message's opaque {@code subjectRef} to the session it names, then to that session's community id. Shared by
 * {@link MessageSubjectPolicyAdapter} and {@link ChatDestinationsAdapter}: both answer a communication-drain port keyed
 * by session and need exactly this walk, so it lives here once rather than as two copies of the same rule.
 *
 * <p>
 * A {@code subjectRef} that does not resolve - unparseable, or naming a session that no longer exists - answers
 * {@link Optional#empty()} rather than throwing. Each caller decides for itself what "nothing to resolve" means for its
 * own port (fail-closed for policy questions, fail-quiet for the chat channel), so that decision stays with the caller;
 * this class only ever reports absence.
 */
@Component
@RequiredArgsConstructor
class SessionSubjectResolver {

    private final SessionRepository sessionRepository;

    Optional<Long> communityIdFor(String organizationId, String subjectRef) {

        return parseSessionId(subjectRef)
                .flatMap(sessionId -> sessionRepository.findByOrganizationIdAndId(organizationId, sessionId))
                .map(SessionEntity::getCommunityId);
    }

    /**
     * {@code subjectRef} is a bare session id for most message types, but the second, seven-days-later
     * {@code SESSION_AWAITING_CLOSE} nudge carries a {@code "<sessionId>:7d"} suffix - otherwise it would collide with
     * the first nudge under {@code communication_message_once}'s {@code (organization_id, message_type, subject_ref)}
     * uniqueness and never get its own message row, which is what {@code communication_delivery_once}'s
     * {@code (message_id, user_id)} then hangs each recipient's single copy off. Only the part before the first
     * {@code :} is ever a session id.
     */
    private static Optional<Long> parseSessionId(String subjectRef) {

        var colon = subjectRef.indexOf(':');
        var sessionIdPart = colon < 0 ? subjectRef : subjectRef.substring(0, colon);
        try {
            return Optional.of(Long.valueOf(sessionIdPart));
        } catch (NumberFormatException notASessionId) {
            return Optional.empty();
        }
    }
}
