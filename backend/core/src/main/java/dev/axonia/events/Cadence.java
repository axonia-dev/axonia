package dev.axonia.events;

import java.time.Period;
import java.util.Objects;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * How often a series recurs: "every {@code count} {@code unit}s" (e.g. every 3 weeks). A fixed interval measured from
 * the series' start date. Owns the two conversions its consumers need: {@link #toPeriod()} for schedule generation and
 * {@link #toDays()} for the community-health expected-gap check.
 */
public record Cadence(@Min(1) @Max(999) int count, @NotNull CadenceUnit unit) {

    private static final int MAX_COUNT = 999;

    public Cadence {
        Objects.requireNonNull(unit, "cadence unit must not be null");
        if (count < 1 || count > MAX_COUNT) {
            throw new IllegalArgumentException("cadence count must be between 1 and 999, was " + count);
        }
    }

    public Period toPeriod() {
        return switch (unit) {
            case DAY -> Period.ofDays(count);
            case WEEK -> Period.ofDays(count * 7);
            case MONTH -> Period.ofMonths(count);
            case YEAR -> Period.ofYears(count);
        };
    }

    public int toDays() {
        return switch (unit) {
            case DAY -> count;
            case WEEK -> count * 7;
            case MONTH -> count * 30;
            case YEAR -> count * 365;
        };
    }
}
