package dev.axonia.events.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import dev.axonia.events.CommunityAccess;
import dev.axonia.events.SessionService;
import lombok.RequiredArgsConstructor;

/**
 * The one place the default upcoming-notice release instant is computed, and the one place that decides what a
 * reschedule does to a stored one.
 *
 * <p>
 * The two live together because the reschedule rule is defined in terms of the default's arithmetic, and the comparison
 * is only sound while both sides come from the same {@link #natural} subtraction. Two copies of it would misclassify
 * every intent they disagreed on - silently, and in the direction that overwrites an organizer's deliberate choice.
 * {@code V33__backfill_session_upcoming_release} deliberately used this exact arithmetic
 * ({@code Instant.minus(n, DAYS)} rather than Postgres {@code make_interval}, which counts calendar days and differs by
 * an hour across a DST boundary) so that a backfilled row still compares equal to its recomputed default.
 *
 * <p>
 * <b>The default.</b> {@code scheduledAt - session_upcoming_lead_days}, stored only when that instant is strictly after
 * the injected {@link Clock}'s now, and {@code null} otherwise. Null is not a missing value: a session created inside
 * its own lead window, or backfilled into the past, is <em>visibly off</em> rather than silently quiet, and the
 * organizer can still send a reminder. The lead time is the session's own community's, read through the
 * organization-explicit {@link CommunityAccess#communicationSettings(String, long)} overload so this answers
 * identically for an agent, a sweep or a browser.
 *
 * <p>
 * <b>A reschedule.</b> On a reschedule the stored value is compared to the <em>bare subtraction</em> from the session's
 * OLD schedule - {@link #natural}, with no future-ness test applied to it. Equal means nobody ever touched it, so it
 * moves with the session under the default's full rule; different means an organizer decided, and their instant is left
 * exactly where it is even if the new schedule now starts before it - the card reports that rather than the system
 * quietly correcting it. The default's "only if still ahead" clause applies when <em>storing</em> the recomputed value,
 * never when deciding whether the old one was touched. Two properties follow, and both are the point of comparing
 * against the bare subtraction rather than against the whole rule:
 *
 * <ul>
 * <li>A deliberate "no reminder" is always preserved. Null is never the result of a subtraction, so a stored null never
 * compares equal and is always read as somebody's decision. Suppressing the reminder for a sensitive session and then
 * moving that session by an hour cannot mail the community.</li>
 * <li>An untouched intent whose instant has already gone by still moves. It equals the bare subtraction from the old
 * schedule regardless of where the clock now sits, so it is recomputed rather than frozen as deliberate and stranded
 * below every future production watermark.</li>
 * </ul>
 *
 * <p>
 * <b>A save that moves nothing decides nothing.</b> The comparison above only has an answer worth acting on once the
 * schedule has actually changed, so an unchanged {@code scheduledAt} short-circuits to the stored value before any of
 * it runs. {@link SessionService#update} is the session's general editor rather than a date-only endpoint - it also
 * carries the duration, the facilitator and the meeting room - so an organizer opening it purely to paste a room would
 * otherwise have an untouched reminder recomputed, and the default's "only if still ahead" clause would return
 * {@code null} for every session already inside its own lead window. The reminder for the session most likely to be
 * given a room would be switched off by an edit that never touched its schedule. The guard sits here, not at any one
 * caller, so every path into a session edit is covered by it.
 *
 * <p>
 * One consequence of deciding "untouched" by comparison rather than by an extra "touched" column, accepted: if a
 * community's {@code session_upcoming_lead_days} changes between a session's creation and its reschedule, the
 * subtraction from the old schedule uses the NEW lead time, so an intent <em>nobody ever chose</em> no longer compares
 * equal and is frozen in place as though it had been deliberate. It stays at the instant the old lead time implied
 * instead of moving with the session. Nothing is mailed that should not be; the reminder simply keeps a schedule the
 * community has since changed its mind about, which is the same direction as the default's own accepted limit that a
 * later lead-time change does not move existing intents.
 *
 * @see SessionCreation
 * @see SessionService#update
 */
@Component
@RequiredArgsConstructor
public class UpcomingReleasePolicy {

    private final CommunityAccess communityAccess;
    private final Clock clock;

    /** The default: the natural instant, but only when it is still ahead. */
    public Instant defaultRelease(String organizationId, long communityId, Instant scheduledAt) {

        return stillAhead(natural(leadDays(organizationId, communityId), scheduledAt));
    }

    /** The intent this session should carry once {@code newScheduledAt} replaces its old schedule. */
    public Instant afterReschedule(SessionEntity session, Instant newScheduledAt) {

        var stored = session.getUpcomingReleaseAt();
        if (newScheduledAt.equals(session.getScheduledAt())) {
            return stored;
        }
        var leadDays = leadDays(session.getOrganizationId(), session.getCommunityId());
        if (!natural(leadDays, session.getScheduledAt()).equals(stored)) {
            return stored;
        }
        return stillAhead(natural(leadDays, newScheduledAt));
    }

    private int leadDays(String organizationId, long communityId) {

        return communityAccess.communicationSettings(organizationId, communityId).sessionUpcomingLeadDays();
    }

    /** The instant the lead time implies, before any question of whether it is still worth storing. Never null. */
    private static Instant natural(int leadDays, Instant scheduledAt) {

        return scheduledAt.minus(leadDays, ChronoUnit.DAYS);
    }

    private Instant stillAhead(Instant releaseAt) {

        return releaseAt.isAfter(clock.instant()) ? releaseAt : null;
    }
}
