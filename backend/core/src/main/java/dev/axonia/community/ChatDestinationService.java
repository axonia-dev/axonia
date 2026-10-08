package dev.axonia.community;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.NotFoundException;
import dev.axonia.ValidationException;
import dev.axonia.communication.ChatChannelProbe;
import dev.axonia.communication.ChatChannelStatus;
import dev.axonia.communication.ChatDestinationsPort.ChatFailure;
import dev.axonia.communication.ChatPlatformName;
import dev.axonia.communication.ChatSendFailedException;
import dev.axonia.communication.ChatWebhooks;
import dev.axonia.community.internal.ChatDestinationEntity;
import dev.axonia.community.internal.ChatDestinationRepository;
import dev.axonia.community.internal.ChatHealthRecorder;
import dev.axonia.community.internal.Communities;
import dev.axonia.community.internal.CommunityEntity;
import dev.axonia.community.internal.MembershipAuthorizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A community's chat destinations: their CRUD, the allowlist check that gates every URL entering the system, and the
 * manual test post. Separate from {@link CommunityPolicyService}, which owns the community's own policy columns - a
 * destination is a row of its own now, and folding its five operations into that class would push it past the size at
 * which it is still one idea.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatDestinationService {

    /** Bounds the drain's expansion, and is far above any real community. */
    static final int MAX_DESTINATIONS = 10;

    /** The broken-destination threshold, carried here because {@code community} cannot see {@code events.internal}. */
    private static final int BROKEN_AFTER = 3;

    /** Shared between {@link #requireNameFree} and {@link #save}'s constraint race, so the two copies cannot drift. */
    private static final String NAME_TAKEN_MESSAGE = "This community already has a destination with that name.";

    private final ChatDestinationRepository destinations;
    private final Communities communities;
    private final MembershipAuthorizer membershipAuthorizer;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final ChatWebhooks chatWebhooks;
    private final ChatChannelProbe chatChannelProbe;
    private final ChatHealthRecorder chatHealthRecorder;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ChatDestination> list(String slug) {

        var organizationId = organizationContext.organizationId();
        var community = authorized(organizationId, slug);
        return destinations.findByOrganizationIdAndCommunityIdOrderByIdAsc(organizationId, community.getId()).stream()
                .map(this::toDestination).toList();
    }

    @Transactional
    public ChatDestination create(String slug, CreateChatDestinationRequest request) {

        var organizationId = organizationContext.organizationId();
        var community = authorized(organizationId, slug);
        var url = requireDeclaredPlatform(request.platform(), request.webhookUrl());
        var name = request.name().trim();

        if (destinations.countByOrganizationIdAndCommunityId(organizationId, community.getId()) >= MAX_DESTINATIONS) {
            throw new ValidationException("CHAT_DESTINATION_LIMIT",
                    "A community can post to at most ten chat destinations. Remove one first.");
        }
        requireNameFree(organizationId, community.getId(), name);

        return toDestination(save(ChatDestinationEntity.builder().organizationId(organizationId)
                .communityId(community.getId()).name(name).webhookUrl(url).build()));
    }

    /**
     * {@link #requireNameFree} is read-then-write: two concurrent creates for the same name can both pass that check
     * and then race the database's own {@code community_chat_destination_named_once} constraint, where one of them
     * turns into a {@link DataIntegrityViolationException} instead of the {@link ValidationException}
     * {@link #requireNameFree} already promises for the same problem, non-concurrently. Deliberately not extended to
     * the {@code MAX_DESTINATIONS} check above: that race has no constraint behind it to catch, an overshoot to eleven
     * is harmless, and the cap is a service-level message by design, not a database invariant.
     *
     * <p>
     * Narrowed to that one constraint by name, the same one {@code ChatDestinationRepositoryTest} asserts against: any
     * other {@code DataIntegrityViolationException} - a genuine outage, a constraint this method knows nothing about -
     * is not this race and must not be misreported as a name collision. It is rethrown unchanged.
     */
    private ChatDestinationEntity save(ChatDestinationEntity entity) {

        try {
            return destinations.save(entity);
        } catch (DataIntegrityViolationException failure) {
            var message = failure.getMostSpecificCause().getMessage();
            if (message != null && message.contains("community_chat_destination_named_once")) {
                throw new ValidationException("CHAT_DESTINATION_NAME_TAKEN", NAME_TAKEN_MESSAGE);
            }
            throw failure;
        }
    }

    /**
     * A blank {@code webhookUrl} keeps the stored one: it is write-only over the API, so a client cannot echo back what
     * it was never given, and an edit that only renames must not have to. Supplying one replaces it and resets the
     * health counters, because the failures they recorded were about a URL axonia will no longer post to.
     */
    @Transactional
    public ChatDestination update(String slug, long id, UpdateChatDestinationRequest request) {

        var organizationId = organizationContext.organizationId();
        var community = authorized(organizationId, slug);
        var entity = ownedOrThrow(organizationId, community.getId(), id);
        var name = request.name().trim();

        if (!name.equals(entity.getName())) {
            requireNameFree(organizationId, community.getId(), name);
        }
        var replacement = ChatWebhookUrls.normalized(request.webhookUrl());
        if (replacement == null) {
            return toDestination(destinations.save(entity.toBuilder().name(name).build()));
        }
        return toDestination(destinations
                .save(entity.toBuilder().name(name).webhookUrl(requireDeclaredPlatform(request.platform(), replacement))
                        .broken(false).consecutiveFailures(0).lastFailureAt(null).lastError(null).build()));
    }

    @Transactional
    public void remove(String slug, long id) {

        var organizationId = organizationContext.organizationId();
        var community = authorized(organizationId, slug);
        destinations.delete(ownedOrThrow(organizationId, community.getId(), id));
    }

    /**
     * Sends one trivial post right now, outside the outbox, so a leader learns whether a destination works without
     * waiting for a session. Deliberately not {@code @Transactional}: the probe's outbound HTTP call must never run
     * inside a database transaction, and the health write recording a FAILURE must survive the very exception that
     * carries it, which an ambient transaction wrapping both would roll back with it.
     *
     * <p>
     * A stored URL that no longer parses - the same defensive case {@link #statusFor} degrades to {@code NotConfigured}
     * for, an older, laxer deploy could have written - is rejected here before the probe ever sees it:
     * {@link ChatChannelProbe#postTest} builds a {@link java.net.URI} straight from the string and throws an unchecked
     * {@code IllegalArgumentException} on one that does not parse, which would otherwise surface as a 500 instead of
     * the 400 a leader can act on.
     */
    public void test(String slug, long id) {

        var organizationId = organizationContext.organizationId();
        var community = authorized(organizationId, slug);
        var entity = ownedOrThrow(organizationId, community.getId(), id);

        if (ChatWebhookUrls.safeHost(entity.getWebhookUrl()) == null) {
            throw new ValidationException("CHAT_DESTINATION_URL_UNPARSEABLE",
                    "This destination's stored URL is no longer valid. Update it before sending a test post.");
        }
        try {
            chatChannelProbe.postTest(entity.getWebhookUrl(), community.getName());
            recordQuietly("successful", id,
                    () -> chatHealthRecorder.recordDelivered(organizationId, id, clock.instant()));
        } catch (ChatSendFailedException failure) {
            recordQuietly("failed", id, () -> chatHealthRecorder.recordFailure(organizationId, id,
                    new ChatFailure(failure.kind(), failure.getMessage(), clock.instant()), BROKEN_AFTER));
            throw failure;
        }
    }

    /**
     * Where one destination's post goes right now, org-explicit for the drain, which runs inside
     * {@code OrganizationScope.runAs} and carries no JWT. A destination removed since a message was queued, or one
     * whose stored URL no longer parses, reads as {@code NotConfigured} rather than throwing into the drain.
     */
    @Transactional(readOnly = true)
    public ChatChannelStatus statusFor(String organizationId, long destinationId) {

        var entity = destinations.findByOrganizationIdAndId(organizationId, destinationId).orElse(null);
        if (entity == null) {
            return new ChatChannelStatus.NotConfigured();
        }
        if (entity.isBroken()) {
            return new ChatChannelStatus.Broken();
        }
        return ChatWebhookUrls.safeHost(entity.getWebhookUrl()) == null
                ? new ChatChannelStatus.NotConfigured()
                : new ChatChannelStatus.Ready(URI.create(entity.getWebhookUrl()));
    }

    /**
     * The community's destination ids, for the drain's expansion. Org-explicit, same no-JWT caller as
     * {@link #statusFor}.
     */
    @Transactional(readOnly = true)
    public List<Long> destinationIdsFor(String organizationId, long communityId) {

        return destinations.findIdsByOrganizationIdAndCommunityId(organizationId, communityId);
    }

    public void recordChatDelivered(String organizationId, long destinationId, Instant at) {

        chatHealthRecorder.recordDelivered(organizationId, destinationId, at);
    }

    public void recordChatFailure(String organizationId, long destinationId, ChatFailure failure, int brokenAfter) {

        chatHealthRecorder.recordFailure(organizationId, destinationId, failure, brokenAfter);
    }

    /** Every entry point resolves the community and checks the same delegated right, so it is resolved in one place. */
    private CommunityEntity authorized(String organizationId, String slug) {

        var entity = communities.findBySlugOrThrow(organizationId, slug);
        membershipAuthorizer.requireOrganizerOrOpenMember(organizationId, entity.getId(), currentUser.userId(),
                entity.isCanManageCommunication());
        return entity;
    }

    private ChatDestinationEntity ownedOrThrow(String organizationId, Long communityId, long id) {

        return destinations.findByOrganizationIdAndId(organizationId, id)
                .filter(entity -> entity.getCommunityId().equals(communityId))
                .orElseThrow(() -> new NotFoundException("No such chat destination."));
    }

    /** The declared platform is never stored: the host is re-read for it on every response, so the two cannot drift. */
    private String requireDeclaredPlatform(ChatPlatformName declared, String raw) {

        var url = ChatWebhookUrls.normalized(raw);
        return ChatWebhookUrls.requireDeclaredPlatform(declared, platformOf(url), url);
    }

    private ChatPlatformName platformOf(String url) {

        return url == null ? null : chatWebhooks.platformOf(url).orElse(null);
    }

    private void requireNameFree(String organizationId, Long communityId, String name) {

        if (destinations.existsByOrganizationIdAndCommunityIdAndName(organizationId, communityId, name)) {
            throw new ValidationException("CHAT_DESTINATION_NAME_TAKEN", NAME_TAKEN_MESSAGE);
        }
    }

    private void recordQuietly(String outcome, long destinationId, Runnable write) {

        try {
            write.run();
        } catch (RuntimeException recordingFailed) {
            log.warn("Recording a {} chat test post for destination {} failed: {}", outcome, destinationId,
                    recordingFailed.getClass().getSimpleName());
        }
    }

    private ChatDestination toDestination(ChatDestinationEntity e) {

        return new ChatDestination(e.getId(), e.getName(), platformOf(e.getWebhookUrl()),
                ChatWebhookUrls.mask(e.getWebhookUrl()), e.isBroken(), e.getLastPostedAt(), e.getLastFailureAt(),
                e.getLastError());
    }
}
