package dev.axonia.events.internal;

import org.springframework.stereotype.Component;

import dev.axonia.events.MeetingPlace;
import dev.axonia.events.OccurrenceSkipService;
import dev.axonia.events.SessionService;
import lombok.RequiredArgsConstructor;

/**
 * Builds every brand-new {@code community_session} row, and stamps it with the default upcoming-notice release intent.
 * The rule itself lives in {@link UpcomingReleasePolicy}, which also owns what a reschedule does to a stored intent:
 * the two are one rule read from two directions, and a second copy of the subtraction here would misclassify every
 * intent the two disagreed on.
 *
 * <p>
 * Null is not a missing value here. A session created inside its own lead window - scheduled for tomorrow in a
 * community that announces three days ahead - or backfilled into the past, is <em>visibly off</em> rather than silently
 * quiet: the organizer sees a card saying no reminder will go out, and can still send one. That is the whole point of
 * storing the intent instead of deriving it, so it must be decided once, at creation, rather than reconstructed later
 * from a formula.
 *
 * <p>
 * <b>Why this class owns construction and not merely the stamp.</b> Three code paths bring a session row into existence
 * - {@link SessionService#createOneOff} (which is also the backfill path: the agent's session-create tool calls it, it
 * does not insert rows of its own), {@link SessionService#ensure} (first-touch materialisation, reached by
 * {@code materialize} too) and {@link OccurrenceSkipService#skip}, which materialises an occurrence purely to mark it
 * skipped. They shared no choke point: each assembled a {@link SessionEntity} itself and handed it to
 * {@link SessionRepository#save}, which also serves every update. A version of this class that only decorated an
 * already-assembled draft left the default one forgotten call to {@code SessionEntity.builder()} away from being
 * silently skipped, with every test still green, so assembling the row is the job rather than a step before it: a
 * caller states what it knows through {@link NewSession} and cannot express a session that never met the rule.
 *
 * <p>
 * That is a strong convention rather than a compiler-enforced invariant, and the difference is worth being honest
 * about: {@code SessionEntity}'s Lombok builder stays public because {@code toBuilder()} is how every update path in
 * this module rewrites a loaded row, and Lombok gives the two one access level. What this buys is that no creation path
 * can assemble a row <em>incidentally</em>; it would have to deliberately reconstruct one this class already knows how
 * to build.
 *
 * <p>
 * A Spring Data JDBC {@code BeforeConvertCallback} was considered and rejected: it would bury a cross-bounded-context
 * read of {@code community}'s communication settings inside persistence conversion, where no reader of
 * {@code createOneOff} would ever look for the rule that decides whether their session announces itself.
 */
@Component
@RequiredArgsConstructor
public class SessionCreation {

    private final UpcomingReleasePolicy upcomingReleasePolicy;

    /** The session row, ready to be saved, carrying the default upcoming release intent. */
    public SessionEntity newSession(NewSession newSession) {

        var place = newSession.place() == null ? MeetingPlace.NONE : newSession.place();
        return SessionEntity.builder().organizationId(newSession.organizationId()).seriesId(newSession.seriesId())
                .communityId(newSession.communityId()).title(newSession.title()).status(newSession.status())
                .scheduledAt(newSession.scheduledAt()).hasTime(newSession.hasTime()).timeZone(newSession.timeZone())
                .occurrenceDate(newSession.occurrenceDate()).durationMinutes(newSession.durationMinutes())
                .location(place.location()).joinUrl(place.joinUrl()).joiningDetails(place.joiningDetails())
                .sections(newSession.sections()).notes("").recordingUrl("")
                .upcomingReleaseAt(upcomingReleasePolicy.defaultRelease(newSession.organizationId(),
                        newSession.communityId(), newSession.scheduledAt()))
                .build();
    }
}
