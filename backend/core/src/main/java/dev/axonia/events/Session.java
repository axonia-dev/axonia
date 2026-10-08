package dev.axonia.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import lombok.Builder;

/**
 * {@code wrapUpStatus} and {@code upcomingStatus} are {@code null} on every projection except the single-session detail
 * read: deriving either costs an outbox query, and the two together cost one community-roster read, all scoped to one
 * session, so a list or schedule page rendering many rows never pays for a card it does not show.
 * {@link dev.axonia.events.internal.SessionMapper#toSession} leaves both null; only
 * {@link dev.axonia.events.internal.SessionMapper#toSessionDetail} resolves them. A null there means "this is not the
 * detail read", never a state - {@link ReleaseState#OFF} is how "no message will go out" is said. See that class's
 * javadoc for the full reasoning.
 *
 * <p>
 * {@code wrapUpReleaseAt} and {@code upcomingReleaseAt} are the two stored release intents a session carries, and they
 * are stored rather than derived for the same reason: a derived instant can only express that a moment arrived, never
 * that somebody decided. {@code null} means "this session sends no such message", in both cases deliberately. See
 * {@link dev.axonia.events.internal.UpcomingReleasePolicy} for where the upcoming one gets its default and what a
 * reschedule does to it.
 *
 * <p>
 * {@code place} is the session's <em>effective</em> place, already resolved by {@link MeetingPlace#resolve}: this reads
 * "where this session meets", never "what override this row stores". A reader cannot tell inheritance from an override
 * here, and deliberately so - the answer a calendar entry, a reminder mail and a join button all want is the same one,
 * and each deriving it would be three chances to disagree. The resolution is all-or-nothing: an occurrence naming any
 * part of a place of its own states the whole of where it meets, and inherits none of its series' standing place, so a
 * new link never travels beside a stale passcode.
 *
 * <p>
 * {@code placeOverride} and {@code seriesPlace} are the two facts that answer was resolved from, kept apart for the one
 * reader the answer does not serve: an edit form. A form seeded from {@code place} cannot tell a session that inherits
 * from one that overrides, so saving it untouched writes the inherited place back as this occurrence's own and detaches
 * it from the series for good, silently. {@code placeOverride} is what this row itself stores -
 * {@link MeetingPlace#NONE} when the session inherits - and {@code seriesPlace} is the standing place it falls back to,
 * {@code NONE} when there is none to fall back to. Both are said through {@link MeetingPlace} rather than read off the
 * columns, so a blank means absence here exactly as it does there and no reader re-derives that rule.
 *
 * <p>
 * {@code seriesTimeZone} is the zone that governs this session's wall clock, and it is {@code null} for a one-off,
 * which belongs to no series and so has none. That distinction is the answer, not a gap: a reader showing or editing an
 * hour needs to know whether a community chose the zone it is read in or whether the viewer's own zone is all there is
 * to go on, and only the difference between a series' zone and no series at all says which.
 *
 * <p>
 * {@code hasTime} says whether anybody ever chose an hour for this occurrence. It is a stored fact rather than a shape
 * a reader infers from {@code scheduledAt}, because the instant of an untimed occurrence is midnight in its series'
 * zone and that is indistinguishable from a series which genuinely meets at midnight. Readers that used to test the
 * instant for midnight UTC were right only while every series was UTC; a series in {@code Europe/Paris} with no
 * preferred time composes {@code 22:00Z}, and the test would have printed a clock nobody chose.
 *
 * <p>
 * {@code timeZone} is this session's OWN clock, which only a one-off has: it is the zone the hour was typed in, and it
 * is null for an occurrence, which reads its series' zone through {@code seriesTimeZone} instead. The two are separate
 * components rather than one resolved value because they answer different questions - "what did somebody choose for
 * this session" and "what does the rhythm it belongs to keep" - and a caller that needs the effective clock takes the
 * first that is present.
 *
 * <p>
 * {@code version} is the row's optimistic-locking counter, carried for the same reason {@code createdAt} and
 * {@code updatedAt} are: it is row metadata the reader may need, and its absence was the anomaly among the three. The
 * calendar feed is the first caller to need it, as the {@code SEQUENCE} an updated occurrence has to advance.
 */
@Builder
public record Session(Long id, Long seriesId, Long communityId, String title, LocalDate occurrenceDate,
        SessionStatus status, Instant scheduledAt, boolean hasTime, Integer durationMinutes, String facilitatorUserId,
        Long attendeeCount, String notes, String recordingUrl, MeetingPlace place, MeetingPlace placeOverride,
        MeetingPlace seriesPlace, String seriesTimeZone, String timeZone, List<Section> sections, Instant createdAt,
        Instant updatedAt, Long version, Instant closedAt, Instant wrapUpReleaseAt, Instant upcomingReleaseAt,
        ReleaseStatus wrapUpStatus, ReleaseStatus upcomingStatus) {

    public Session {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
