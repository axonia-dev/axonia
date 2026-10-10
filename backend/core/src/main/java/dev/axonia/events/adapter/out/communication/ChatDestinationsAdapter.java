package dev.axonia.events.adapter.out.communication;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.communication.api.ChatChannelStatus;
import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.spi.ChatDestinationsPort;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Resolves a message's opaque {@code subjectRef} to the community whose chat destinations it belongs to, and reports
 * back what became of a post to one of them. Lives here for the same reason {@code MessageSubjectPolicyAdapter} does:
 * only {@code events} can walk a session id to a community, and {@code events} already depends on
 * {@code communication}, so this adds no module edge.
 *
 * <p>
 * A subject that resolves to nothing - an unparseable ref, a deleted session - has no destinations rather than
 * throwing. A gate that blew up on a deleted session would fail the row instead of suppressing it, and retry it every
 * pass forever. That case is ruled out by {@link SessionSubjectResolver} itself; a community that outlives the session
 * pointing at it - which can only happen if {@code community}'s own invariant (sessions carry no cascade delete, and a
 * community with sessions is archived rather than removed) is ever broken - is what {@link #destinationsQuietly} still
 * guards against, the same way {@code MessageSubjectPolicyAdapter#classEnabled} catches {@link CommunityAccess}'s
 * {@code NotFoundException}.
 */
@Component
@RequiredArgsConstructor
class ChatDestinationsAdapter implements ChatDestinationsPort {

    private static final Set<MessageType> CHANNEL_TYPES = Set.of(MessageType.SESSION_UPCOMING,
            MessageType.SESSION_WRAP_UP);

    /** The broken-destination threshold, expressed where the policy is, not where the count is stored. */
    private static final int BROKEN_AFTER = 3;

    private final SessionSubjectResolver sessionSubjectResolver;
    private final CommunityAccess communityAccess;

    @Override
    public Set<MessageType> channelTypes() {

        return CHANNEL_TYPES;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> destinationsFor(String organizationId, String subjectRef) {

        return sessionSubjectResolver.communityIdFor(organizationId, subjectRef)
                .map(id -> destinationsQuietly(organizationId, id)).orElseGet(List::of);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatChannelStatus statusFor(String organizationId, long destinationId) {

        return communityAccess.chatDestinationStatus(organizationId, destinationId);
    }

    @Override
    public void recordDelivered(String organizationId, long destinationId, Instant at) {

        communityAccess.recordChatDelivered(organizationId, destinationId, at);
    }

    @Override
    public void recordFailure(String organizationId, long destinationId, ChatFailure failure) {

        communityAccess.recordChatFailure(organizationId, destinationId, failure, BROKEN_AFTER);
    }

    private List<Long> destinationsQuietly(String organizationId, long communityId) {

        try {
            return communityAccess.chatDestinationIds(organizationId, communityId);
        } catch (NotFoundException communityNoLongerExists) {
            return List.of();
        }
    }
}
