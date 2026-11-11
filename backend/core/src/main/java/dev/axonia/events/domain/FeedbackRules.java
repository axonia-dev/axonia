package dev.axonia.events.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class FeedbackRules {

    public static final Duration WINDOW = Duration.ofDays(7);

    public static final int MIN_REVEAL = 3;

    private static final String HELD = "HELD";

    private FeedbackRules() {
    }

    public enum WindowState {
        OPEN, CLOSED, NONE
    }

    /** Empty when the session was never held, or was closed too late for its hour to be remembered. */
    public static Optional<Instant> closesAt(SessionRecord session, Duration wrapUpMaxAge) {

        var closedAt = session.getClosedAt();
        if (!HELD.equals(session.getStatus()) || closedAt == null
                || closedAt.isAfter(session.getScheduledAt().plus(wrapUpMaxAge))) {
            return Optional.empty();
        }
        return Optional.of(closedAt.plus(WINDOW));
    }

    public static WindowState windowState(SessionRecord session, Duration wrapUpMaxAge, Instant now) {

        return closesAt(session, wrapUpMaxAge)
                .map(closes -> now.isBefore(closes) ? WindowState.OPEN : WindowState.CLOSED).orElse(WindowState.NONE);
    }

    public static double toTen(int value, int scale) {

        return 1 + (value - 1) * 9.0 / (scale - 1);
    }

    public static boolean isScore(int value, int scale) {

        return value >= 1 && value <= scale;
    }
}
