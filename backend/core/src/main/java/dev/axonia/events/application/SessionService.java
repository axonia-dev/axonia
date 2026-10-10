package dev.axonia.events.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.CreateOneOffRequest;
import dev.axonia.events.api.MeetingPlace;
import dev.axonia.events.api.PastSessionsRequest;
import dev.axonia.events.api.Session;
import dev.axonia.events.api.Sessions;
import dev.axonia.events.api.TopicStatus;
import dev.axonia.events.application.port.out.ActionItemStore;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SeriesStore;
import dev.axonia.events.application.port.out.SessionListing;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.application.port.out.TopicStore;
import dev.axonia.events.domain.Cadence;
import dev.axonia.events.domain.LateBindingPin;
import dev.axonia.events.domain.NewSession;
import dev.axonia.events.domain.NextSlot;
import dev.axonia.events.domain.NextSlotQuery;
import dev.axonia.events.domain.OccurrenceInstant;
import dev.axonia.events.domain.SectionRecord;
import dev.axonia.events.domain.SeriesRecord;
import dev.axonia.events.domain.SeriesStatus;
import dev.axonia.events.domain.SessionCancelled;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.events.domain.SessionRescheduled;
import dev.axonia.events.domain.SlotRecord;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.metrics.UsageCounters;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SessionService implements Sessions {

    private final SessionStore sessionStore;
    private final SeriesStore seriesStore;
    private final TopicStore topicStore;
    private final ActionItemStore actionItemStore;
    private final CheckpointDecisionPass checkpointDecisionPass;
    private final SessionMapper sessionMapper;
    private final SessionListing sessionListing;
    private final SessionCreation sessionCreation;
    private final SessionAuthorizer sessionAuthorizer;
    private final CommunityAccess communityAccess;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final UpcomingReleasePolicy upcomingReleasePolicy;
    private final SessionInstant sessionInstant;
    private final UsageCounters usageCounters;
    private final SectionTypeCatalog sectionTypeCatalog;
    private final FacilitatorResolver facilitatorResolver;

    @Transactional
    public Session materialize(Long seriesId) {

        var organizationId = organizationContext.organizationId();
        var series = series(organizationId, seriesId);
        requireMember(series.getCommunityId());

        var now = clock.instant();
        var cadence = new Cadence(series.getCadenceCount(), series.getCadenceUnit());
        var zone = ZoneId.of(series.getTimeZone());
        var nextInstant = NextSlot
                .suggest(NextSlotQuery.builder().cadence(cadence).startsOn(series.getStartsOn())
                        .preferredTime(series.getPreferredTime()).zone(zone).now(now).build())
                .orElseThrow(() -> new ConflictException("SERIES_NOT_SCHEDULABLE",
                        "Series has no next slot within the schedule horizon"));
        return ensure(seriesId, OccurrenceInstant.anchorOf(nextInstant, zone));
    }

    @Transactional
    public Session ensure(Long seriesId, LocalDate occurrenceDate) {

        var organizationId = organizationContext.organizationId();
        var series = series(organizationId, seriesId);
        requireMember(series.getCommunityId());

        if (series.getStatus() != SeriesStatus.ACTIVE) {
            throw new ConflictException("SERIES_NOT_ACTIVE", "Cannot open a session on a non-active series");
        }

        var existing = sessionStore.findByOrganizationIdAndSeriesIdAndOccurrenceDate(organizationId, seriesId,
                occurrenceDate);
        if (existing.isPresent()) {
            return sessionMapper.toSession(existing.get());
        }

        var at = OccurrenceInstant.at(occurrenceDate, series.getPreferredTime(), ZoneId.of(series.getTimeZone()));
        try {
            var saved = sessionStore.save(sessionCreation.newSession(NewSession.builder().organizationId(organizationId)
                    .seriesId(seriesId).communityId(series.getCommunityId()).status("SCHEDULED")
                    .occurrenceDate(occurrenceDate).scheduledAt(at).hasTime(series.getPreferredTime() != null)
                    .durationMinutes(series.getDurationMinutes()).sections(sessionMapper.snapshotSections(series))
                    .build()));
            return sessionMapper.toSession(saved);
        } catch (DataIntegrityViolationException ex) {
            return sessionMapper.toSession(sessionStore
                    .findByOrganizationIdAndSeriesIdAndOccurrenceDate(organizationId, seriesId, occurrenceDate)
                    .orElseThrow(() -> ex));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Session get(Long id) {

        return sessionMapper.toSession(findOrThrow(organizationContext.organizationId(), id));
    }

    /**
     * {@link #get}, plus the release status of both member-facing messages - the state, the current recipient count,
     * and, once sent, the outcome. Reserved for the session detail page, the one place those cards are shown:
     * {@code bookmarks}' and {@code knowledge}'s own {@code SessionAccessAdapter}s call {@link #get} instead, because
     * neither renders a release card and neither should pay for {@link SessionMapper#toSessionDetail}'s extra outbox
     * and roster reads.
     */
    @Transactional(readOnly = true)
    public Session getDetail(Long id) {

        return sessionMapper.toSessionDetail(findOrThrow(organizationContext.organizationId(), id));
    }

    /**
     * Both list pages read through {@link SessionListing} rather than through the aggregate: a page of sessions is a
     * four-level aggregate per row, and Spring Data JDBC loads it one query per parent row per collection - 587
     * statements for a page of 20, measured. The reader reads one query per level instead. The count beside it stays a
     * repository query: it touches no child table at all.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Session> upcoming(String slug, Pageable pageable) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        var now = clock.instant();
        var rows = sessionListing.page(SessionPageQuery.builder().organizationId(organizationId)
                .communityId(communityId).now(now).window(SessionPageQuery.Window.UPCOMING).pageable(pageable).build());
        var total = sessionStore.countUpcoming(organizationId, communityId, now);
        return new PageImpl<>(rows, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Session> past(PastSessionsRequest request) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(request.slug());
        var now = clock.instant();
        var pageable = request.pageable();
        var rows = sessionListing.page(SessionPageQuery.builder().organizationId(organizationId)
                .communityId(communityId).now(now).from(request.from()).to(request.to()).status(request.status())
                .window(SessionPageQuery.Window.PAST).pageable(pageable).build());
        var total = sessionStore
                .countPast(PastSessionsQuery.builder().organizationId(organizationId).communityId(communityId).now(now)
                        .from(request.from()).to(request.to()).status(request.status()).build());
        return new PageImpl<>(rows, pageable, total);
    }

    /**
     * The one path that moves an existing session's {@code scheduledAt}, so the one path that has to answer what
     * happens to a reminder: a reminder nobody ever touched moves with the session, one an organizer chose stays where
     * they put it. {@link UpcomingReleasePolicy#afterReschedule} decides which of the two this is, including the case
     * where the schedule did not move at all: this is the session's general editor rather than a date-only endpoint, so
     * a save that only changes the room or the duration must leave the reminder exactly where it was.
     *
     * <p>
     * Returns {@link SessionMapper#toSessionDetail} for the reason {@link #close} and every
     * {@link SessionReleaseService} operation do: this is a mutation that can change a release state, so its response
     * should carry the statuses rather than the null that means "not the detail read". A caller reading it back to see
     * where the reminder landed would otherwise be told nothing.
     */
    @Transactional
    public Session update(Long sessionId, UpdateSessionRequest request) {

        var organizationId = organizationContext.organizationId();
        var session = findOrThrow(organizationId, sessionId);
        sessionAuthorizer.requireOrganizer(session, currentUser.userId());
        requireNotCancelled(session, "This session is cancelled");

        var place = request.place() == null ? MeetingPlace.NONE : request.place();
        var scheduledAt = sessionInstant.of(session, request.scheduledAt());
        var facilitatorUserId = facilitatorResolver.resolve(session.getCommunityId(), session.getFacilitatorUserId(),
                request.facilitatorUserId());
        var saved = sessionStore.save(session.toBuilder().scheduledAt(scheduledAt)
                .hasTime(request.scheduledAt().time() != null)
                .upcomingReleaseAt(upcomingReleasePolicy.afterReschedule(session, scheduledAt))
                .durationMinutes(request.durationMinutes()).facilitatorUserId(facilitatorUserId)
                .location(place.location()).joinUrl(place.joinUrl()).joiningDetails(place.joiningDetails()).build());
        eventPublisher
                .publishEvent(new SessionRescheduled(saved.getId(), saved.getCommunityId(), saved.getScheduledAt()));
        return sessionMapper.toSessionDetail(saved);
    }

    @Transactional
    public Session cancel(Long sessionId) {

        var organizationId = organizationContext.organizationId();
        var session = findOrThrow(organizationId, sessionId);
        sessionAuthorizer.requireOrganizer(session, currentUser.userId());

        requireNotCancelled(session, "Session is already cancelled");
        if (sessionMapper.isHeld(session)) {
            throw new ConflictException("SESSION_ALREADY_HELD", "Cannot cancel a session that has already been held");
        }
        var now = clock.instant();
        return sessionMapper.toSession(cancelSession(organizationId, session, session.toBuilder(), now));
    }

    /**
     * The shared "call it off" routine: every slotted non-{@code DONE} topic returns to the backlog, the row is saved
     * as {@code CANCELLED}, and {@link SessionCancelled} is published. Used by both {@link #cancel} and the "no" answer
     * of {@link #close}, so the two paths cannot drift - a fizzled session releases its topics the same way regardless
     * of which endpoint said so.
     *
     * <p>
     * A cancelled session answers nothing, so every item that named it as its checkpoint goes back to late binding
     * rather than waiting on a session that will not happen.
     */
    private SessionRecord cancelSession(String organizationId, SessionRecord original,
            SessionRecord.SessionRecordBuilder builder, Instant instant) {

        var sections = Objects.requireNonNullElse(original.getSections(), List.<SectionRecord>of());
        sections.stream().flatMap(sec -> Objects.requireNonNullElse(sec.getSlots(), List.<SlotRecord>of()).stream())
                .map(SlotRecord::getTopicId).filter(Objects::nonNull)
                .forEach(topicId -> topicStore.findByOrganizationIdAndId(organizationId, topicId)
                        .filter(t -> t.getStatus() != TopicStatus.DONE)
                        .ifPresent(t -> topicStore.save(t.toBuilder().status(TopicStatus.PROPOSED).build())));
        var saved = sessionStore.save(builder.status("CANCELLED").build());
        actionItemStore.clearPinsFor(organizationId, original.getCommunityId(), original.getId(), instant);
        eventPublisher.publishEvent(new SessionCancelled(saved.getId(), saved.getCommunityId()));
        return saved;
    }

    @Transactional
    public Session recordFollowUp(Long sessionId, FollowUpRequest request) {

        var organizationId = organizationContext.organizationId();
        var session = findOrThrow(organizationId, sessionId);
        sessionAuthorizer.requireOrganizer(session, currentUser.userId());

        requireNotCancelled(session, "Cannot record follow-up on a cancelled session");
        requireHeld(session, "Follow-up can only be recorded after the session is held");
        var sections = Objects.requireNonNullElse(session.getSections(), List.<SectionRecord>of());

        var saved = sessionStore.save(session.toBuilder().notes(Objects.requireNonNullElse(request.notes(), ""))
                .recordingUrl(Objects.requireNonNullElse(request.recordingUrl(), "")).build());

        // The talk happened, so it is done. There is no longer an action-shaped exception here: an action item
        // never occupied a slot, and is answered through the decisions below instead.
        sections.stream().flatMap(sec -> Objects.requireNonNullElse(sec.getSlots(), List.<SlotRecord>of()).stream())
                .map(SlotRecord::getTopicId).filter(Objects::nonNull).distinct()
                .forEach(topicId -> topicStore.findByOrganizationIdAndId(organizationId, topicId)
                        .ifPresent(t -> topicStore.save(t.toBuilder().status(TopicStatus.DONE).build())));

        checkpointDecisionPass.apply(session, request.decisions(), currentUser.userId(), clock.instant());
        return sessionMapper.toSession(saved);
    }

    @Override
    @Transactional
    public Session createOneOff(String slug, CreateOneOffRequest request) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        var userId = currentUser.userId();
        var canCreate = communityAccess.isOrganizer(communityId, userId)
                || (communityAccess.contributionPolicy(communityId).canStartSeries()
                        && communityAccess.isMember(communityId, userId));
        if (!canCreate) {
            throw new ForbiddenException("Only an organizer may create a one-off session");
        }
        request.sections().forEach(t -> sectionTypeCatalog.require(organizationId, t.sectionTypeId()));
        var sections = request.sections().stream().map(t -> SectionRecord.builder().sectionTypeId(t.sectionTypeId())
                .title(t.title()).durationMinutes(t.durationMinutes()).notes(null).slots(new ArrayList<>()).build())
                .toList();
        var saved = sessionStore.save(sessionCreation.newSession(
                NewSession.builder().organizationId(organizationId).communityId(communityId).title(request.title())
                        .status("SCHEDULED").scheduledAt(sessionInstant.ofOneOff(request.scheduledAt())).hasTime(true)
                        .timeZone(request.scheduledAt().zone()).durationMinutes(request.durationMinutes())
                        .place(request.place()).sections(sections).build()));
        return sessionMapper.toSession(saved);
    }

    /**
     * The organizer's confirmation that a past session took place, or their "no" that it did not. A {@code held} answer
     * stamps the wrap-up release at the community's grace period from now; a "no" runs through the same
     * {@link #cancelSession} routine as the dedicated cancel endpoint instead of a separate status, so
     * {@code closedAt > scheduledAt} later tells "called off in advance" apart from "fizzled and nobody said so", and
     * the two "call it off" paths cannot silently diverge. Both branches return {@link SessionMapper#toSessionDetail}:
     * this is the one mutation outside {@link SessionReleaseService} that changes wrap-up state, so its response should
     * carry {@code wrapUpStatus} rather than leave it null.
     */
    @Transactional
    public Session close(Long sessionId, CloseSessionRequest request) {

        var organizationId = organizationContext.organizationId();
        var entity = findOrThrow(organizationId, sessionId);
        sessionAuthorizer.requireOrganizer(entity, currentUser.userId());

        if (sessionMapper.isSettled(entity)) {
            throw new ConflictException("SESSION_ALREADY_CLOSED", "This session has already been closed");
        }
        var now = clock.instant();
        if (entity.getScheduledAt().isAfter(now)) {
            throw new ConflictException("SESSION_NOT_YET_DUE", "A session cannot be closed before it starts");
        }

        var builder = entity.toBuilder().closedAt(now);
        if (!request.held()) {
            return sessionMapper.toSessionDetail(cancelSession(organizationId, entity, builder, now));
        }
        var grace = communityAccess.communicationSettings(entity.getCommunityId()).wrapUpGraceHours();
        var saved = sessionStore
                .save(builder.status("HELD").wrapUpReleaseAt(now.plus(grace, ChronoUnit.HOURS)).build());
        // The one moment a late binding resolves. HELD is asserted by an organizer and never inferred from the clock,
        // so "the next session" becomes this session here and nowhere else.
        actionItemStore.pinLateBound(LateBindingPin.builder().organizationId(organizationId)
                .communityId(entity.getCommunityId()).sessionId(saved.getId()).build(), entity.getScheduledAt(), now);
        // Only the held branch, and only after this method's transaction commits. The "no" branch above returns
        // through cancelSession before reaching this line, so a session called off is never counted as one held.
        usageCounters.recordAfterCommit(UsageCounters.Event.SESSION_HELD);
        return sessionMapper.toSessionDetail(saved);
    }

    private SessionRecord findOrThrow(String organizationId, Long id) {

        return sessionStore.findByOrganizationIdAndId(organizationId, id)
                .orElseThrow(() -> new NotFoundException("Session not found: " + id));
    }

    private SeriesRecord series(String organizationId, Long seriesId) {

        return seriesStore.findByOrganizationIdAndId(organizationId, seriesId)
                .orElseThrow(() -> new NotFoundException("Series not found: " + seriesId));
    }

    private void requireMember(long communityId) {

        if (!communityAccess.isMember(communityId, currentUser.userId())) {
            throw new ForbiddenException("Community membership required");
        }
    }

    private void requireNotCancelled(SessionRecord session, String message) {

        if ("CANCELLED".equals(session.getStatus())) {
            throw new ConflictException("SESSION_CANCELLED", message);
        }
    }

    private void requireHeld(SessionRecord session, String message) {

        if (!sessionMapper.isHeld(session)) {
            throw new ConflictException("SESSION_NOT_HELD", message);
        }
    }
}
