package dev.axonia.events.application;

import java.time.Clock;
import java.time.Instant;

import dev.axonia.events.domain.SessionRecord;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ValidationException;

/**
 * The bound on a reminder: it may be released at any instant strictly between now and the session's own start, and
 * anything outside that window is refused. {@link SessionReleaseService#setUpcomingRelease} is the one caller.
 *
 * <p>
 * This is deliberately not {@link WrapUpReleaseClamp}, which takes a past instant and stores {@code now} instead. A
 * wrap-up describes something already finished, so "in the past" sensibly reads as "send it now". A reminder is only
 * useful before the thing it announces, so a past pick, or one landing after the session has begun, is a mistake worth
 * refusing rather than reinterpreting - reinterpreting it would send a "coming up" mail about a session that has
 * already started.
 *
 * <p>
 * The two out-of-window refusals carry distinct codes because they are distinct organizer mistakes with distinct
 * remedies: the dialog sat open long enough for the pick to go stale, versus a pick that was always past the session's
 * start. Both are {@link ValidationException} (400) rather than a conflict: the session is in a perfectly good state,
 * the instant is simply not admissible. The states where no reminder can be sent at all are a different answer, and
 * {@link #requireOpen} gives it as a {@link ConflictException} before any instant is looked at.
 *
 * <p>
 * "Send now" never reaches the bound as an instant. It is a flag on {@link UpcomingReleaseRequest} the server answers
 * with its own {@link Clock}, so it is admissible by construction wherever the session has not yet started - see that
 * record's javadoc for why the browser's clock is not trusted to say "now" here. {@code null} - no reminder - is
 * likewise always admissible and passes straight through.
 *
 * <p>
 * A payload carrying both is refused rather than resolved by precedence. The two are different answers to the same
 * question, so a client sending both is broken, and silently keeping one would throw away a perfectly good pick with
 * nothing to show for it. Saying so is more useful to whoever has to fix that client than a documented winner.
 */
public final class UpcomingReleaseWindow {

    private UpcomingReleaseWindow() {
    }

    public static Instant admissible(UpcomingReleaseRequest request, SessionRecord session, Clock clock) {

        requireOpen(session, clock);
        if (request.sendNow()) {
            if (request.releaseAt() != null) {
                throw new ValidationException("UPCOMING_RELEASE_AMBIGUOUS",
                        "A reminder is either sent now or scheduled for an instant, not both");
            }
            return clock.instant();
        }
        var releaseAt = request.releaseAt();
        if (releaseAt == null) {
            return null;
        }
        if (!releaseAt.isAfter(clock.instant())) {
            throw new ValidationException("UPCOMING_RELEASE_IN_PAST",
                    "A reminder cannot be scheduled for an instant that has already passed");
        }
        if (!releaseAt.isBefore(session.getScheduledAt())) {
            throw new ValidationException("UPCOMING_RELEASE_AFTER_START",
                    "A reminder has to go out before the session starts");
        }
        return releaseAt;
    }

    /**
     * The window has to exist before anything can land in it. A session that is cancelled, skipped or already held, or
     * one whose start has passed, sends no reminder at all - {@code findUpcomingNoticeSessionIds} keeps both its
     * {@code status = 'SCHEDULED'} and its {@code scheduled_at > :now} guards - so all three answers are refused there
     * rather than storing a decision that could only ever be a lie. These are {@code ConflictException}s (409): the
     * request is fine, the session's state is what makes it impossible.
     */
    private static void requireOpen(SessionRecord session, Clock clock) {

        if (!"SCHEDULED".equals(session.getStatus())) {
            throw new ConflictException("SESSION_NOT_SCHEDULED",
                    "A reminder can only be set on a session that is still scheduled");
        }
        if (!session.getScheduledAt().isAfter(clock.instant())) {
            throw new ConflictException("SESSION_ALREADY_STARTED",
                    "A reminder cannot be changed once the session has started");
        }
    }
}
