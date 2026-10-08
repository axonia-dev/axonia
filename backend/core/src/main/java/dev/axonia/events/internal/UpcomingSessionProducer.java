package dev.axonia.events.internal;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.OrganizationResolver;
import dev.axonia.communication.CommunicationPort;
import dev.axonia.communication.MessageType;
import dev.axonia.communication.OutboundMessage;
import dev.axonia.communication.ProductionWatermark;
import dev.axonia.events.CommunityAccess;
import lombok.RequiredArgsConstructor;

/**
 * Decides who cares about a scheduled session coming up soon, and enqueues one {@code SESSION_UPCOMING} message per
 * such session, addressed to every member of its community.
 *
 * <p>
 * The notice's due instant is the session's own stored {@code upcomingReleaseAt}, not an expression over the
 * community's lead time, and {@link SessionRepository#findUpcomingNoticeSessionIds} narrows to sessions whose intent
 * falls in {@code (since, now]}, {@code since} coming from {@link ProductionWatermark#since}. A session with no intent
 * is never produced at all.
 *
 * <p>
 * Unlike {@link WrapUpProducer} and {@code AwaitingCloseProducer}, this producer takes {@link java.time.Instant#EPOCH}
 * as its first-run floor rather than a configured max-age. That is safe for two independent reasons now. The query's
 * own {@code scheduledAt > now} guard keeps a first run, or a run after a long outage, from ever matching a session
 * that has already started or passed, regardless of how far back {@code since} reaches - see that query's own javadoc
 * for the boundary reasoning. On top of that, an intent is stored only when it is still ahead at creation, so an
 * unbounded {@code since} reaches nothing but instants somebody meant to be claimable. A session already claimed on an
 * earlier tick never reappears; a re-run before the watermark advances is still a no-op against the outbox's own
 * {@code (organization_id, message_type, subject_ref)} unique key.
 *
 * <p>
 * {@code produce} carries its own {@code @Transactional} - see {@link WrapUpProducer}'s class javadoc for exactly why:
 * the identical reasoning applies here, this producer's enqueues and its {@link ProductionWatermark#advanceTo} call
 * must commit together.
 *
 * <p>
 * {@code organizationId} is resolved from the shared-kernel {@link OrganizationResolver}, not {@code events}' own
 * request-scoped {@code OrganizationContext}: this runs inside the communication sweep's
 * {@code OrganizationScope.runAs}, which carries no JWT and installs a {@code ProvisioningAuthentication} instead -
 * exactly the {@code OrganizationBoundAuthentication} shape {@link OrganizationResolver} already resolves generically,
 * the same seam {@code config.PersistenceConfig} uses to set the RLS session variable for the same call.
 */
@Component
@RequiredArgsConstructor
class UpcomingSessionProducer {

    static final String PRODUCER_KEY = "session-upcoming";

    private final SessionRepository sessionRepository;
    private final CommunityAccess communityAccess;
    private final SessionFactsAssembler factsAssembler;
    private final CommunicationPort port;
    private final OrganizationResolver organizationResolver;
    private final ProductionWatermark watermark;

    @Transactional
    void produce(Instant now) {

        var organizationId = organizationId();
        var since = watermark.since(PRODUCER_KEY, Instant.EPOCH);
        for (var sessionId : sessionRepository.findUpcomingNoticeSessionIds(organizationId, now, since)) {
            sessionRepository.findByOrganizationIdAndId(organizationId, sessionId)
                    .ifPresent(entity -> enqueue(organizationId, entity));
        }
        watermark.advanceTo(PRODUCER_KEY, now);
    }

    private void enqueue(String organizationId, SessionEntity entity) {

        var recipients = communityAccess.memberUserIds(organizationId, entity.getCommunityId());
        if (recipients.isEmpty()) {
            return;
        }
        var facts = factsAssembler.facts(organizationId, entity);
        port.enqueue(OutboundMessage.builder().type(MessageType.SESSION_UPCOMING)
                .subjectRef(String.valueOf(entity.getId())).recipientUserIds(recipients)
                .template(ReleasableMessage.UPCOMING.template()).facts(facts).build());
    }

    private String organizationId() {

        return organizationResolver.currentOrganization().orElseThrow(
                () -> new IllegalStateException("UpcomingSessionProducer must run inside an Organization scope"));
    }
}
