package dev.axonia.events.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.communication.api.MessageOutcome;
import dev.axonia.communication.api.MessageOutcomePort;
import dev.axonia.communication.api.MessageType;
import dev.axonia.events.api.MeetingPlace;
import dev.axonia.events.api.ReleaseState;
import dev.axonia.events.api.ReleaseStatus;
import dev.axonia.events.api.Section;
import dev.axonia.events.api.Session;
import dev.axonia.events.api.SessionStatus;
import dev.axonia.events.api.Slot;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.domain.MeetingPlaces;
import dev.axonia.events.domain.PlannedSection;
import dev.axonia.events.domain.SectionRecord;
import dev.axonia.events.domain.SectionTypeRecord;
import dev.axonia.events.domain.SeriesRecord;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.events.domain.SlotPresenter;
import dev.axonia.events.domain.SlotRecord;
import lombok.RequiredArgsConstructor;

/**
 * Maps the {@link SessionRecord} domain record to the module-public {@link Session}/{@link Section}/{@link Slot} DTOs,
 * deriving the read-time status, the attendee count, and the per-section type label. Shared by {@code SessionService}
 * and {@code SlotService}.
 *
 * <p>
 * {@link #toSession} and {@link #toSessionDetail} are deliberately two methods, not one method with a flag. Every list
 * and schedule endpoint in this module - a month of sessions, a community's upcoming or past pages - maps rows through
 * {@link #toSession}, which never touches the outbox or the community roster. {@link #toSessionDetail} is reserved for
 * single-session reads and writes: {@code SessionService}'s {@code getDetail}, {@code update} and {@code close}, and
 * every {@code SessionReleaseService} operation. Those are the calls whose answer a release card is rendered from, so
 * they are the ones that should pay for {@link MessageOutcomePort#outcomeFor} and
 * {@link CommunityAccess#memberUserIds}. Wiring the outbox lookup into {@link #toSession} directly would make every
 * list render an N+1 query against the outbox - a schedule page showing a month of sessions would pay for it once per
 * row, for a card none of those rows ever display.
 *
 * <p>
 * {@link #toSessions} exists for the same reason from the other direction. A session's effective place needs its
 * series' standing place, which is one read per row if each row asks for itself, so the list projections map through
 * the batch method and pay for one read over the whole page instead.
 */
@Component
@RequiredArgsConstructor
public class SessionMapper {

    private final CommunityAccess communityAccess;
    private final MessageOutcomePort messageOutcomePort;
    private final SessionBatchLookups sessionBatchLookups;

    /**
     * {@link #toSession} for a single row, resolved as a one-element batch so this and {@link #toSessions} share one
     * code path rather than two: {@link SessionBatchLookups}'s lookups below all guard the empty-collection case, so
     * wrapping a lone entity in a one-element list costs nothing extra.
     */
    @Transactional(readOnly = true)
    public Session toSession(SessionRecord e) {

        return toSessions(List.of(e)).getFirst();
    }

    /**
     * {@link #toSession} for a whole batch, reading every series, section type and attendance count the batch mentions
     * in one query apiece rather than one per row or one per section. Every list projection in this module goes through
     * here - a month of schedule entries, a community's upcoming and past pages - and the standing place a session
     * inherits, a section's type label, and a session's attendee count are each the same handful of facts shared across
     * the whole page, so resolving them row by row (or section by section) would be an N+1 for an answer already in
     * hand. The actual resolution is {@link SessionBatchLookups}'s job; this class only maps.
     */
    @Transactional(readOnly = true)
    public List<Session> toSessions(List<SessionRecord> entities) {

        var lookups = sessionBatchLookups.lookupsOf(entities);
        return entities.stream().map(e -> toSession(e, null, null, lookups)).toList();
    }

    /**
     * The series id is asked for explicitly rather than handed straight to {@link Map#get}, because a one-off session
     * has none and an immutable map rejects a null key outright: a page of nothing but one-offs used to end in a
     * {@code NullPointerException} rather than in a page of sessions with no place.
     */
    private SessionBatchLookups.Standing standingPlaceIn(Map<Long, SessionBatchLookups.Standing> standingPlaces,
            Long seriesId) {

        if (seriesId == null) {
            return SessionBatchLookups.Standing.NO_SERIES;
        }
        return standingPlaces.getOrDefault(seriesId, SessionBatchLookups.Standing.NO_SERIES);
    }

    /**
     * {@link #toSession}, plus the release status of both member-facing messages a session can send: for each, the
     * state, the current recipient count, and - once sent - the outcome. See this class's javadoc for why this is a
     * separate method rather than a flag on {@link #toSession}.
     *
     * <p>
     * The roster is read once and shared by both: {@code recipientCount} answers "how many members would this reach
     * today", which is the same number for the wrap-up and the reminder. Only the outbox lookup is genuinely per
     * message type.
     */
    @Transactional(readOnly = true)
    public Session toSessionDetail(SessionRecord e) {

        var recipientCount = communityAccess.memberUserIds(e.getOrganizationId(), e.getCommunityId()).size();
        return toSession(e,
                releaseStatus(MessageType.SESSION_WRAP_UP, e.getId(), e.getWrapUpReleaseAt(), recipientCount),
                releaseStatus(MessageType.SESSION_UPCOMING, e.getId(), e.getUpcomingReleaseAt(), recipientCount),
                sessionBatchLookups.lookupsOf(List.of(e)));
    }

    /**
     * The place is said three ways, all three through {@link MeetingPlace}: the effective one a join button follows,
     * the override this row itself stores, and the standing place it would fall back to. An edit form needs the last
     * two kept apart, because seeding a field from the effective place turns "this session inherits" into "this session
     * overrides" the moment the form is saved. Routing all three through the one resolver is what keeps a blank column
     * meaning absence in every one of them without any caller restating the rule.
     */
    private Session toSession(SessionRecord e, ReleaseStatus wrapUpStatus, ReleaseStatus upcomingStatus,
            SessionBatchLookups.BatchLookups lookups) {

        var standing = standingPlaceIn(lookups.standingPlaces(), e.getSeriesId());
        var standingPlace = standing.place();
        var attendees = lookups.attendeeCounts().getOrDefault(e.getId(), 0L);
        var sections = new ArrayList<Section>();
        var list = e.getSections() == null ? List.<SectionRecord>of() : e.getSections();
        for (var i = 0; i < list.size(); i++) {
            sections.add(toSection(e, list.get(i), i, lookups.sectionTypes()));
        }
        return Session.builder().id(e.getId()).seriesId(e.getSeriesId()).communityId(e.getCommunityId())
                .title(e.getTitle()).occurrenceDate(e.getOccurrenceDate()).status(derivedStatus(e))
                .scheduledAt(e.getScheduledAt()).hasTime(e.isHasTime()).durationMinutes(e.getDurationMinutes())
                .facilitatorUserId(e.getFacilitatorUserId()).attendeeCount(attendees).notes(e.getNotes())
                .recordingUrl(e.getRecordingUrl()).place(MeetingPlace.resolve(MeetingPlaces.of(e), standingPlace))
                .placeOverride(MeetingPlaces.of(e)).seriesPlace(standingPlace).seriesTimeZone(standing.timeZone())
                .timeZone(e.getTimeZone()).sections(sections).createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .version(e.getVersion()).closedAt(e.getClosedAt()).wrapUpReleaseAt(e.getWrapUpReleaseAt())
                .upcomingReleaseAt(e.getUpcomingReleaseAt()).wrapUpStatus(wrapUpStatus).upcomingStatus(upcomingStatus)
                .build();
    }

    /**
     * The four-state table 0050 built for the wrap-up, read against whichever {@link MessageType} is asked for - the
     * wrap-up and the upcoming notice share it rather than each carrying a copy. The states mean the same thing for
     * both, because they are read from the outbox's own counts rather than the clock.
     *
     * <p>
     * That is the fix for the bug that made this derivation necessary: {@code ReleasePanel} used to derive its whole
     * statement from {@code wrapUpReleaseAt} alone, so a release instant that had already passed with nothing yet
     * enqueued read as "sent" the instant "Send now" was pressed, when the write had only made the row eligible - the
     * sweep acts on it up to a tick later. Comparing {@code releaseAt} against {@code now} anywhere in here would
     * reintroduce that exact bug, which is why this method takes no clock at all. The same holds for the reminder, and
     * more visibly: a session whose {@code upcomingReleaseAt} is already behind the producer's watermark still reads
     * {@code SCHEDULED} until the outbox actually has rows for it, because the producer may simply not have ticked yet.
     *
     * <p>
     * {@link MessageOutcomePort#outcomeFor}'s {@code pending} count already folds in {@code FAILED} rows alongside
     * {@code PENDING} and {@code SENDING} - see that port's javadoc - so checking it is exactly "any row not yet
     * terminal" and needs no enum-by-enum reconstruction here.
     *
     * <p>
     * {@code couldNotReach} folds in {@link MessageOutcome#unreachable()} (no usable address - a real delivery failure)
     * but deliberately never {@link MessageOutcome#suppressed()}: that count is governance - a member's own opt-out, a
     * community or organization switched off, a message type this deploy no longer recognizes - working as designed,
     * not a failure, and disclosing it would tell an organizer how many of their members privately chose not to receive
     * the mail. See {@link MessageOutcome}'s javadoc for why the outbox already keeps the two apart.
     */
    private ReleaseStatus releaseStatus(MessageType type, Long sessionId, Instant releaseAt, int recipientCount) {

        var outcome = messageOutcomePort.outcomeFor(type, String.valueOf(sessionId));
        return ReleaseStatus.builder().state(releaseState(outcome, releaseAt)).recipientCount(recipientCount)
                .sent(outcome.sent()).couldNotReach(outcome.unreachable() + outcome.abandoned()).build();
    }

    private ReleaseState releaseState(MessageOutcome outcome, Instant releaseAt) {

        if (outcome.pending() > 0) {
            return ReleaseState.QUEUED;
        }
        if (outcome.sent() > 0 || outcome.suppressed() > 0 || outcome.unreachable() > 0 || outcome.abandoned() > 0) {
            return ReleaseState.SENT;
        }
        return releaseAt == null ? ReleaseState.OFF : ReleaseState.SCHEDULED;
    }

    /**
     * Whether somebody has asserted that this session took place. Not derived from the clock: a session whose
     * {@code scheduledAt} has drifted into the past is not held until an organizer says so, because rendering a page
     * can tolerate a wrong guess but mailing a community cannot.
     */
    public boolean isHeld(SessionRecord s) {

        return "HELD".equals(s.getStatus());
    }

    /**
     * Whether this session's lifecycle has already been settled - closed out as held, or called off. Sits beside
     * {@link #isHeld} for the same reason: both answer "what has somebody asserted about this session", from the stored
     * row and never from the clock, and a session drifting past its scheduled time settles nothing on its own.
     * {@code CANCELLED} counts even without a {@code closedAt}, because every row cancelled before the V26 backfill
     * still carries a null one.
     */
    public boolean isSettled(SessionRecord s) {

        return s.getClosedAt() != null || "CANCELLED".equals(s.getStatus());
    }

    private SessionStatus derivedStatus(SessionRecord s) {

        return SessionStatus.valueOf(s.getStatus());
    }

    private Section toSection(SessionRecord session, SectionRecord s, int position,
            Map<Long, SectionTypeRecord> sectionTypes) {

        var type = s.getSectionTypeId() == null ? null : sectionTypes.get(s.getSectionTypeId());
        var label = type == null ? null : type.getLabel();
        var icon = type == null ? null : type.getIcon();
        var accentColor = type == null ? null : type.getAccentColor();
        var slotEntities = s.getSlots() == null ? List.<SlotRecord>of() : s.getSlots();
        var slots = new ArrayList<Slot>();
        for (var i = 0; i < slotEntities.size(); i++) {
            slots.add(toSlot(s, slotEntities.get(i), i));
        }
        return Section.builder().id(s.getId()).sessionId(session.getId()).position(position)
                .sectionTypeId(s.getSectionTypeId()).sectionType(label).sectionTypeIcon(icon)
                .sectionTypeAccentColor(accentColor).title(s.getTitle()).durationMinutes(s.getDurationMinutes())
                .slots(slots).build();
    }

    /**
     * Snapshots a series' {@code defaultStructure} into persistable {@link SectionRecord} rows for a materialized
     * session: empty mutable slot lists, no ids. Shared by session opening and occurrence unskip.
     */
    public List<SectionRecord> snapshotSections(SeriesRecord series) {

        var refs = Objects.requireNonNullElse(series.getDefaultStructure(), List.<PlannedSection>of());
        return refs.stream()
                .map(r -> SectionRecord.builder().sectionTypeId(r.getSectionTypeId()).title(r.getTitle())
                        .durationMinutes(r.getDurationMinutes()).notes(null).slots(new ArrayList<SlotRecord>()).build())
                .toList();
    }

    /**
     * Projects a series' {@code defaultStructure} into {@link Section} DTOs as they would appear on a materialized
     * session: section type label resolved, sequential position, empty slots, no ids (virtual occurrence).
     */
    @Transactional(readOnly = true)
    public List<Section> projectSections(SeriesRecord series) {

        var refs = series.getDefaultStructure() == null ? List.<PlannedSection>of() : series.getDefaultStructure();
        var types = sessionBatchLookups.sectionTypesOfRefs(series.getOrganizationId(), refs);
        var sections = new ArrayList<Section>();
        for (var i = 0; i < refs.size(); i++) {
            var ref = refs.get(i);
            var type = types.get(ref.getSectionTypeId());
            var label = type == null ? null : type.getLabel();
            var icon = type == null ? null : type.getIcon();
            var accentColor = type == null ? null : type.getAccentColor();
            sections.add(Section.builder().position(i).sectionTypeId(ref.getSectionTypeId()).sectionType(label)
                    .sectionTypeIcon(icon).sectionTypeAccentColor(accentColor).title(ref.getTitle())
                    .durationMinutes(ref.getDurationMinutes()).slots(List.of()).build());
        }
        return sections;
    }

    private Slot toSlot(SectionRecord section, SlotRecord s, int position) {

        var presenters = Objects.requireNonNullElse(s.getPresenters(), Set.<SlotPresenter>of()).stream()
                .map(SlotPresenter::getUserId).sorted().toList();
        return Slot.builder().id(s.getId()).sectionId(section.getId()).position(position).presenterUserIds(presenters)
                .topicId(s.getTopicId()).title(s.getTitle()).notes(s.getNotes()).durationMinutes(s.getDurationMinutes())
                .tags(SessionSections.tags(s)).build();
    }
}
