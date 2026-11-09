package dev.axonia.health.domain;

/** The word a question's status shows, each bound to its one level. */
public enum StatusCode {

    TOO_FEW_TALKS(StatusLevel.QUIET), ONE_VOICE(StatusLevel.WORRY), LEANS_ON_ONE(StatusLevel.WATCH), SHARED(
            StatusLevel.GOOD), SHARED_STAGE(
                    StatusLevel.GREAT), DORMANT(StatusLevel.WORRY), STARTING_UP(StatusLevel.QUIET), STALLED(
                            StatusLevel.WORRY), SLOWING(StatusLevel.WATCH), PICKING_UP(StatusLevel.GREAT), ON_RHYTHM(
                                    StatusLevel.GOOD), PAYING_OFF(StatusLevel.GREAT), STARTING_TO_PAY_OFF(
                                            StatusLevel.GOOD), SAVED_NOT_CONFIRMED(StatusLevel.WATCH), NO_PAYOFF_YET(
                                                    StatusLevel.QUIET), NOTHING_OPENED(StatusLevel.QUIET);

    private final StatusLevel level;

    StatusCode(StatusLevel level) {

        this.level = level;
    }

    public StatusLevel level() {

        return level;
    }
}
