package dev.axonia.events.internal;

import java.time.Instant;

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
 * Decides who cares about a held session's wrap-up, and enqueues one {@code SESSION_WRAP_UP} message per such session,
 * addressed to every member of its community. {@link SessionRepository#findWrapUpSessionIds} only ever matches a
 * session that carries a {@code wrapUpReleaseAt} in the past - a held session with none set never appears here, a
 * permanent suppression, not a delay.
 *
 * <p>
 * {@code since} comes from {@link ProductionWatermark#since}, not a fixed rolling window - a session whose
 * {@code wrapUpReleaseAt} this producer already claimed through on an earlier tick never reappears, because the
 * watermark advances past it. {@link CommunicationWindows#wrapUpMaxAge()} still bounds the very first tick this
 * organization ever runs (no stored watermark) and any tick that follows a long outage (a stale but still-present one):
 * without that floor, an absent watermark would fall back to {@link java.time.Instant#EPOCH}-style unboundedness and
 * match every session this community has ever held.
 *
 * <p>
 * <b>This is a deliberate behaviour change.</b> A member who joins the community after a session's wrap-up was already
 * released never receives it: their own user id would have had no earlier outbox row to conflict with under the old
 * rolling-window design, so the very next tick used to mail them regardless of when they joined. Once the watermark has
 * advanced past a session's release instant, that session's id simply never appears in
 * {@link SessionRepository#findWrapUpSessionIds}'s result again, for anyone - mailing somebody about a session held
 * before they were a member is noise, not a courtesy owed to them.
 *
 * <p>
 * {@code produce} carries its own {@code @Transactional}: every enqueue this tick performs, and the
 * {@link ProductionWatermark#advanceTo} call that follows them, commit together or not at all. Producers now run
 * outside any tick-wide transaction ({@code SweepUnit}'s own class javadoc explains why), so nothing above this method
 * closes that boundary on its behalf - if it did not open one itself, a crash after this tick's last enqueue but before
 * {@code advanceTo} would still leave the watermark unmoved (safe: the next tick re-claims the identical window, and
 * the outbox's unique key makes every re-enqueue a no-op), but a crash that let {@code advanceTo} commit while an
 * earlier enqueue in the same loop had not would silently and permanently skip that session's wrap-up - worse than
 * re-deriving the window. Wrapping the whole method closes that gap: either everything this tick claimed lands,
 * together with the new watermark, or none of it does.
 *
 * <p>
 * See {@link UpcomingSessionProducer}'s class javadoc for why {@code organizationId} comes from the shared-kernel
 * {@link OrganizationResolver} rather than {@code events}' own JWT-only {@code OrganizationContext}.
 */
@Component
@RequiredArgsConstructor
class WrapUpProducer {

    static final String PRODUCER_KEY = "session-wrap-up";

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
        var floor = now.minus(communicationWindows.wrapUpMaxAge());
        var since = watermark.since(PRODUCER_KEY, floor);
        for (var sessionId : sessionRepository.findWrapUpSessionIds(organizationId, now, since)) {
            sessionRepository.findByOrganizationIdAndId(organizationId, sessionId)
                    .ifPresent(entity -> enqueue(organizationId, entity, now));
        }
        watermark.advanceTo(PRODUCER_KEY, now);
    }

    private void enqueue(String organizationId, SessionEntity entity, Instant now) {

        var recipients = communityAccess.memberUserIds(organizationId, entity.getCommunityId());
        if (recipients.isEmpty()) {
            return;
        }
        var facts = factsAssembler.factsForWrapUp(organizationId, entity, now);
        port.enqueue(OutboundMessage.builder().type(MessageType.SESSION_WRAP_UP)
                .subjectRef(String.valueOf(entity.getId())).recipientUserIds(recipients)
                .template(ReleasableMessage.WRAP_UP.template()).facts(facts).build());
    }

    private String organizationId() {

        return organizationResolver.currentOrganization()
                .orElseThrow(() -> new IllegalStateException("WrapUpProducer must run inside an Organization scope"));
    }
}
