package dev.axonia.events;

import java.time.Instant;

/**
 * The organizer's answer to "when does the reminder go out", as one of the three mutually exclusive answers the release
 * card offers:
 *
 * <ul>
 * <li>{@code {"sendNow": true}} - release it immediately.</li>
 * <li>{@code {"releaseAt": "..."}} - release it at that instant, which is bounded strictly between now and the
 * session's own start.</li>
 * <li>{@code {}} - no reminder for this session. {@code releaseAt} null is the suppression answer, not a missing
 * value.</li>
 * </ul>
 *
 * <p>
 * {@code sendNow} is a flag rather than a {@code releaseAt} carrying the browser's clock, which is how the wrap-up's
 * "Send it now" is spelled. The wrap-up can afford that because it clamps a past instant up to the server clock; this
 * one deliberately refuses one instead, so a browser running two seconds slow would have its "send now" rejected as a
 * pick in the past. Saying "now" and letting the server stamp its own {@code Clock} takes clock skew out of the
 * contract entirely, and the server's clock is the only one the production watermark ever compares against.
 *
 * <p>
 * The three are mutually exclusive: a payload setting {@code sendNow} <em>and</em> a {@code releaseAt} is refused by
 * {@link dev.axonia.events.internal.UpcomingReleaseWindow} rather than resolved by precedence. The flag is a normalised
 * {@code Boolean} rather than a bare {@code boolean} because Jackson refuses to map an absent property onto a
 * primitive, and leaving the flag out entirely is how both of the other two answers are spelled.
 */
public record UpcomingReleaseRequest(Boolean sendNow, Instant releaseAt) {

    public UpcomingReleaseRequest {
        sendNow = Boolean.TRUE.equals(sendNow);
    }
}
