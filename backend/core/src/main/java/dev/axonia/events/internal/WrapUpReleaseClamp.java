package dev.axonia.events.internal;

import java.time.Clock;
import java.time.Instant;

import dev.axonia.events.SessionReleaseService;

/**
 * A non-null wrap-up {@code releaseAt} more than a produce interval in the past is never claimed again: production
 * claims through {@code wrap_up_release_at > :since}, and {@code since} trails the real clock by up to the produce
 * interval in steady state (five minutes, as of the two-cadence split). "Send it now" writing the BROWSER's clock, or
 * an organizer deliberately picking a past instant, would otherwise fall below every future watermark and strand the
 * session in {@code SCHEDULED} forever - permanently, silently never sent, the exact dishonesty this clamp exists to
 * eliminate. {@link SessionReleaseService#setWrapUpRelease} calls {@link #clampToNow} before every write so that never
 * happens: a past instant is stored as {@code now} instead, which stays genuinely claimable on the very next produce
 * tick. A future instant is returned unchanged, and {@code null} - a permanent suppression - is never touched.
 *
 * <p>
 * Extracted from its caller rather than inlined there: the clock must be the injected {@link Clock}, never
 * {@code Instant.now()}, the same convention every other temporal decision in this module already follows.
 */
public final class WrapUpReleaseClamp {

    private WrapUpReleaseClamp() {
    }

    public static Instant clampToNow(Instant releaseAt, Clock clock) {

        if (releaseAt == null) {
            return null;
        }
        var now = clock.instant();
        return releaseAt.isBefore(now) ? now : releaseAt;
    }
}
