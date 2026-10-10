package dev.axonia.events.application;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.Session;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.ReleasableMessage;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Everything an organizer decides about a session's outgoing member mail: when each {@link ReleasableMessage} goes out,
 * whether it goes out at all, and what it will look like when it does.
 *
 * <p>
 * Split out of {@link SessionService} rather than left there. The three operations share one subject - the release
 * intent stored on the session row - one authorisation floor, and one trigger: an organizer who came to the release
 * card and said so. That last part is the line, and it is narrower than "everything that knows a session sends mail".
 * The intent is ALSO written from five lifecycle-driven places that deliberately stay in {@link SessionService},
 * {@code OccurrenceSkipService} and {@code SessionCreation}, because none of them is an organizer answering a question
 * about mail:
 *
 * <ul>
 * <li>{@link SessionService#close} stamps {@code wrapUpReleaseAt} at the community's grace period when the organizer
 * confirms the session was held.</li>
 * <li>{@link SessionService#update} recomputes {@code upcomingReleaseAt} through
 * {@code UpcomingReleasePolicy.afterReschedule} whenever the session moves.</li>
 * <li>All three creation paths stamp the default upcoming release through {@code SessionCreation}:
 * {@code createOneOff}, {@code ensure} (reached by {@code materialize} too), and {@code OccurrenceSkipService.skip}.
 * They share one implementation, so a rule change reaches all three at once - the count matters for auditing, not for
 * keeping them consistent with each other.</li>
 * </ul>
 *
 * <p>
 * Anybody changing what instants are admissible therefore has SEVEN writers to keep in step, not the two below. The
 * rules themselves live in {@link WrapUpReleaseClamp}, {@link UpcomingReleaseWindow} and {@code UpcomingReleasePolicy}
 * for exactly that reason - they are the shared definitions those writers reach, and a tightening applied here instead
 * of there would silently leave the lifecycle stamps on the old rule.
 *
 * <p>
 * The split also keeps both classes inside the module's class-complexity ceiling, which {@link SessionService} was one
 * operation away from when the preview gained a second message type, but that is the smaller half of the reason: a
 * class this cohesive would be worth extracting at any complexity.
 *
 * <p>
 * Every method returns {@link SessionMapper#toSessionDetail}, never the plain {@link SessionMapper#toSession}: each one
 * can change a release state, and a caller reading the response for "did that take" needs the statuses populated rather
 * than the null that means "not the detail read". All three are single-session reads, so no list pays for it.
 */
@Service
@RequiredArgsConstructor
public class SessionReleaseService {

    private final SessionStore sessionStore;
    private final SessionMapper sessionMapper;
    private final SessionAuthorizer sessionAuthorizer;
    private final SessionMessagePreviewer previewer;
    private final CommunityAccess communityAccess;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    /**
     * Moving the wrap-up release to any instant - "send it now", "send it Friday", or {@code null} to suppress the
     * wrap-up for this session permanently. The instant is written through as given, null included: a permanent
     * suppression is a deliberate feature, not a missing value.
     *
     * <p>
     * A non-null {@code releaseAt} is passed through {@link WrapUpReleaseClamp#clampToNow} before it is stored - see
     * that class's javadoc for why storing a past instant verbatim would silently strand the session below every future
     * production watermark, never sent.
     */
    @Transactional
    public Session setWrapUpRelease(Long sessionId, WrapUpReleaseRequest request) {

        var entity = authorizedSession(sessionId);
        requireHeld(entity);

        var releaseAt = WrapUpReleaseClamp.clampToNow(request.releaseAt(), clock);
        return sessionMapper.toSessionDetail(sessionStore.save(entity.toBuilder().wrapUpReleaseAt(releaseAt).build()));
    }

    /**
     * {@link #setWrapUpRelease}'s counterpart for the reminder: "send it now", "send it Thursday morning", or
     * {@code null} to give this session no reminder at all. Same authorisation, same detail response.
     *
     * <p>
     * The two differ in what they accept, deliberately. {@link WrapUpReleaseClamp} takes a past instant and stores
     * {@code now}, because a wrap-up describes something already finished. {@link UpcomingReleaseWindow} refuses one,
     * because a reminder is only useful before the thing it announces; see that class for the bound and its refusal
     * codes.
     *
     * <p>
     * {@link UpcomingReleaseWindow} also answers the analogue of {@link #setWrapUpRelease}'s {@link #requireHeld},
     * which is why it takes the whole session rather than an instant: each operation is available exactly while its
     * message is still meaningful, and once a session has started, or has been cancelled, skipped or held, there is no
     * window left for any answer to land in. Inside one, all three answers are admissible, "send now" included.
     */
    @Transactional
    public Session setUpcomingRelease(Long sessionId, UpcomingReleaseRequest request) {

        var entity = authorizedSession(sessionId);

        var releaseAt = UpcomingReleaseWindow.admissible(request, entity, clock);
        return sessionMapper
                .toSessionDetail(sessionStore.save(entity.toBuilder().upcomingReleaseAt(releaseAt).build()));
    }

    /**
     * Renders the message this session would send, of the type the caller names, for the person looking at it right
     * now. {@code messageType} is a {@link ReleasableMessage} wire code, and anything else is refused there rather than
     * defaulted to one of them.
     *
     * <p>
     * Authorised exactly like the two setters - the same {@code canManageCommunication} floor - because a rendered
     * message carries another member's name and whatever was written in the follow-up notes, and the one thing worse
     * than nobody being able to preview a send is the wrong person being able to. Unlike the setters, this requires no
     * particular session state: previewing before deciding whether to release at all is exactly the moment this exists
     * for, and {@link SessionMessagePreviewer} renders a session with no content - no talks, no action points, nothing
     * - rather than throwing, so there is no state this can fail to render for.
     *
     * <p>
     * {@code slug} is resolved and checked against the session's own community the same way every other
     * {@code /communities/{slug}/...} method in this module does, rather than accepted and ignored: a slug that names a
     * real community the caller is not looking at this session through must 404, the same as a slug that names no
     * community at all, not silently render the session anyway.
     *
     * <p>
     * {@code @Transactional} even though this never writes: {@code MessageRenderPort#renderEnqueued} decides whether an
     * outbox row already exists by reading through a connection scoped by the {@code app.current_org} GUC, not by the
     * {@code organizationId} this method already resolved - see that method's own javadoc for why an unscoped
     * connection would answer "no row" rather than throw. Wrapping the whole call in one transaction means the lookup
     * and every read around it share the exact one connection {@code OrganizationAwareDataSource} scoped when it was
     * opened, closing the only window in which this method's own request-scoped organization and the connection's GUC
     * could ever disagree.
     */
    @Transactional
    public MessagePreview preview(String slug, Long sessionId, String messageType) {

        var message = ReleasableMessage.of(messageType);
        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        var entity = findOrThrow(organizationId, sessionId);
        sessionAuthorizer.requireCommunityMatches(entity, communityId);
        sessionAuthorizer.requireCommunicationManager(entity, currentUser.userId());

        return previewer.preview(organizationId, entity, currentUser.userId(), message);
    }

    private SessionRecord authorizedSession(Long sessionId) {

        var entity = findOrThrow(organizationContext.organizationId(), sessionId);
        sessionAuthorizer.requireCommunicationManager(entity, currentUser.userId());
        return entity;
    }

    private SessionRecord findOrThrow(String organizationId, Long sessionId) {

        return sessionStore.findByOrganizationIdAndId(organizationId, sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
    }

    private void requireHeld(SessionRecord entity) {

        if (!sessionMapper.isHeld(entity)) {
            throw new ConflictException("SESSION_NOT_HELD", "Wrap-up release can only be set once the session is held");
        }
    }
}
