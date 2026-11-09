package dev.axonia.health.domain;

import java.time.Instant;

/** The per-question status rules. Each reading is checked top down and the first matching row wins. */
public final class StatusRules {

    static final int MIN_TALKS = 3;
    static final int SHARED_STAGE_SPEAKERS = 3;
    static final int PAYING_OFF_HELPED = 3;

    private StatusRules() {
    }

    /** Who carries it: read from the talks distribution. */
    public static Status stage(Distribution stage) {

        if (stage.talks() < MIN_TALKS) {
            return Status.of(StatusCode.TOO_FEW_TALKS);
        }
        if (stage.speakers() == 1) {
            return Status.of(StatusCode.ONE_VOICE);
        }
        if (leansOnOne(stage.topShare())) {
            return Status.of(StatusCode.LEANS_ON_ONE);
        }
        if (stage.speakersForHalf() >= SHARED_STAGE_SPEAKERS) {
            return Status.of(StatusCode.SHARED_STAGE);
        }
        return Status.of(StatusCode.SHARED);
    }

    /** True when the top speaker gave strictly more than half the talks; exactly half is shared. */
    public static boolean leansOnOne(double topShare) {

        return topShare > 0.5 + 1e-9;
    }

    /**
     * Is it alive: the rhythm, with a community that has never held a session but has one booked read as starting up.
     * {@code lastHeldAt} is the latest session ever held, null when there has been none.
     */
    public static Status alive(CadenceStatus rhythm, Instant lastHeldAt, boolean sessionAhead,
            Trend.Direction peopleDirection) {

        return switch (rhythm) {
            case DORMANT -> Status.of(StatusCode.DORMANT);
            case STALLED ->
                lastHeldAt == null && sessionAhead ? Status.of(StatusCode.STARTING_UP) : Status.of(StatusCode.STALLED);
            case SLOWING -> Status.of(StatusCode.SLOWING);
            case STEADY -> peopleDirection == Trend.Direction.UP
                    ? Status.of(StatusCode.PICKING_UP)
                    : Status.of(StatusCode.ON_RHYTHM);
        };
    }

    /** Is it paying off: checked deepest first, since helped, saves and openings can coexist in any combination. */
    public static Status payoff(long opened, long saves, long helped) {

        if (helped >= PAYING_OFF_HELPED) {
            return Status.of(StatusCode.PAYING_OFF);
        }
        if (helped > 0) {
            return Status.of(StatusCode.STARTING_TO_PAY_OFF);
        }
        if (saves > 0) {
            return Status.of(StatusCode.SAVED_NOT_CONFIRMED);
        }
        if (opened > 0) {
            return Status.of(StatusCode.NO_PAYOFF_YET);
        }
        return Status.of(StatusCode.NOTHING_OPENED);
    }
}
