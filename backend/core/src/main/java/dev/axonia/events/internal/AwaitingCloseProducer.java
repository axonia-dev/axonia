package dev.axonia.events.internal;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.OrganizationResolver;
import dev.axonia.communication.CommunicationPort;
import dev.axonia.communication.CommunicationWindows;
import dev.axonia.communication.MessageType;
import dev.axonia.communication.OutboundMessage;
import dev.axonia.communication.ProductionWatermark;
import dev.axonia.events.CommunityAccess;
import lombok.RequiredArgsConstructor;

/**
 * Decides who should be nudged about a scheduled session nobody has closed out, and enqueues one
 * {@code SESSION_AWAITING_CLOSE} message per such session, addressed to its facilitator and its community's organizers.
 * This nudge is load-bearing, not a courtesy: no community setting opts it out (unlike the other two messages,
 * {@link dev.axonia.events.internal.MessageSubjectPolicyAdapter} always answers {@code true} for this type), and it
 * fires twice - once a day past {@code scheduledAt}, once again seven days past - then stops.
 *
 * <p>
 * The two firings are two independent arms, each with its own due instant ({@code scheduledAt + 1 day},
 * {@code scheduledAt + 7 days}) and its own {@link ProductionWatermark} row: {@code "session-awaiting-close-1d"} and
 * {@code "session-awaiting-close-7d"} never share a watermark, because a session crossing the one-day due instant this
 * tick has no bearing on when it crosses the seven-day one. What still makes each firing happen exactly once per
 * recipient, independent of the watermark, is the outbox's pair of unique keys:
 * {@code (organization_id, message_type, subject_ref)} on the message, so the two firings are two distinct messages,
 * and {@code (message_id, user_id)} on the delivery, so one organizer gets one copy of each. The one-day nudge always
 * enqueues under the bare session id, the seven-day nudge under {@code "<id>:7d"} (a distinct key so it does not
 * collide with, and get silently dropped by, the first). {@link MessageSubjectPolicyAdapter} already parses a
 * {@code subjectRef} up to its first {@code :}, exactly matching this suffix.
 *
 * <p>
 * {@code floor}, bound from {@link CommunicationWindows#awaitingCloseMaxAge()}, is each arm's first-run and long-outage
 * backstop the same way {@link WrapUpProducer}'s is: without one, a newly promoted organizer would be nudged about
 * every session the community has ever left open, the very first time this organization's watermark row for that arm
 * does not yet exist.
 *
 * <p>
 * {@code produce} carries its own {@code @Transactional}, wrapping both arms in one transaction rather than two - see
 * {@link WrapUpProducer}'s class javadoc for why an enqueue and its watermark advance must never commit apart. A crash
 * between the two arms rolls both back together; the next tick re-claims both windows from their last-advanced
 * watermarks, and the outbox's unique key makes every re-enqueue in the already-completed arm a no-op.
 *
 * <p>
 * See {@link UpcomingSessionProducer}'s class javadoc for why {@code organizationId} comes from the shared-kernel
 * {@link OrganizationResolver} rather than {@code events}' own JWT-only {@code OrganizationContext}.
 */
@Component
@RequiredArgsConstructor
class AwaitingCloseProducer {

    private static final String SEVEN_DAY_SUFFIX = ":7d";
    private static final Arm ONE_DAY_ARM = new Arm("session-awaiting-close-1d", 1, "", "ONE_DAY");
    private static final Arm SEVEN_DAY_ARM = new Arm("session-awaiting-close-7d", 7, SEVEN_DAY_SUFFIX, "SEVEN_DAY");

    private final SessionRepository sessionRepository;
    private final CommunityAccess communityAccess;
    private final SessionFactsAssembler factsAssembler;
    private final CommunicationPort port;
    private final OrganizationResolver organizationResolver;
    private final CommunicationWindows communicationWindows;
    private final ProductionWatermark watermark;

    @Transactional
    void produce(Instant now) {

        var organizationId = organizationId();
        var floor = now.minus(communicationWindows.awaitingCloseMaxAge());
        nudge(organizationId, now, floor, ONE_DAY_ARM);
        nudge(organizationId, now, floor, SEVEN_DAY_ARM);
    }

    private void nudge(String organizationId, Instant now, Instant floor, Arm arm) {

        var since = watermark.since(arm.producerKey(), floor);
        for (var sessionId : sessionRepository.findOverdueUnclosedSessionIds(organizationId, arm.offsetDays(), since,
                now)) {
            sessionRepository.findByOrganizationIdAndId(organizationId, sessionId)
                    .ifPresent(entity -> enqueue(organizationId, entity, arm));
        }
        watermark.advanceTo(arm.producerKey(), now);
    }

    /**
     * One nudge's own identity: its watermark key, its due-instant offset in days, its outbox subject suffix, and the
     * token the mail renders as "A day on" or "A week on". The token is stamped here, at production, because that is
     * the one thing about a nudge's age that never changes afterwards.
     */
    private record Arm(String producerKey, int offsetDays, String subjectSuffix, String nudgeArm) {
    }

    private void enqueue(String organizationId, SessionEntity entity, Arm arm) {

        var recipients = recipients(organizationId, entity);
        if (recipients.isEmpty()) {
            return;
        }
        var facts = factsAssembler.factsForAwaitingClose(organizationId, entity, arm.nudgeArm());
        port.enqueue(OutboundMessage.builder().type(MessageType.SESSION_AWAITING_CLOSE)
                .subjectRef(entity.getId() + arm.subjectSuffix()).recipientUserIds(recipients)
                .template("session-awaiting-close").facts(facts).build());
    }

    /**
     * The facilitator plus the community's organizers, deduplicated - a facilitator who is also an organizer is nudged
     * once, not twice.
     */
    private List<String> recipients(String organizationId, SessionEntity entity) {

        var recipients = new LinkedHashSet<String>(
                communityAccess.organizerUserIds(organizationId, entity.getCommunityId()));
        var facilitatorUserId = entity.getFacilitatorUserId();
        if (facilitatorUserId != null && !facilitatorUserId.isBlank()) {
            recipients.add(facilitatorUserId);
        }
        return List.copyOf(recipients);
    }

    private String organizationId() {

        return organizationResolver.currentOrganization().orElseThrow(
                () -> new IllegalStateException("AwaitingCloseProducer must run inside an Organization scope"));
    }
}
