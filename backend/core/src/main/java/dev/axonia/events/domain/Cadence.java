package dev.axonia.events.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * How often a series recurs: "every {@code count} {@code unit}s" (e.g. every 3 weeks), measured from the series' start
 * date. Owns the two answers its consumers need: {@link #occurrence} for schedule generation and {@link #toDays()} for
 * the community-health expected-gap check.
 *
 * <p>
 * {@code monthlyBy} exists only for a monthly cadence and is normalised away for every other unit. A monthly cadence
 * that states none keeps its day number, which is what every monthly series meant before the rule existed.
 */
public record Cadence(@Min(1) @Max(999) int count, @NotNull CadenceUnit unit, MonthlyRule monthlyBy) {

    private static final int MAX_COUNT = 999;
    private static final int DAYS_PER_WEEK = 7;
    private static final int LAST_ORDINAL = 5;

    public Cadence {
        Objects.requireNonNull(unit, "cadence unit must not be null");
        if (count < 1 || count > MAX_COUNT) {
            throw new IllegalArgumentException("cadence count must be between 1 and 999, was " + count);
        }
        monthlyBy = unit == CadenceUnit.MONTH ? Objects.requireNonNullElse(monthlyBy, MonthlyRule.DAY_OF_MONTH) : null;
    }

    public Cadence(int count, CadenceUnit unit) {
        this(count, unit, null);
    }

    /**
     * The {@code index}-th occurrence of a series starting on {@code startsOn}, the start itself being the 0th. Every
     * occurrence is measured from the start rather than from the one before it, so a 31st clamped into a short month
     * returns to the 31st afterwards instead of staying on the 28th for good.
     */
    public LocalDate occurrence(LocalDate startsOn, int index) {

        var steps = (long) count * index;
        return switch (unit) {
            case DAY -> startsOn.plusDays(steps);
            case WEEK -> startsOn.plusWeeks(steps);
            case MONTH ->
                monthlyBy == MonthlyRule.WEEKDAY_OF_MONTH ? sameWeekday(startsOn, steps) : startsOn.plusMonths(steps);
            case YEAR -> startsOn.plusYears(steps);
        };
    }

    /** A fifth weekday reads as the last one, because most months have only four of each. */
    private static LocalDate sameWeekday(LocalDate startsOn, long months) {

        var weekday = startsOn.getDayOfWeek();
        var ordinal = (startsOn.getDayOfMonth() - 1) / DAYS_PER_WEEK + 1;
        var adjuster = ordinal == LAST_ORDINAL
                ? TemporalAdjusters.lastInMonth(weekday)
                : TemporalAdjusters.dayOfWeekInMonth(ordinal, weekday);
        return YearMonth.from(startsOn).plusMonths(months).atDay(1).with(adjuster);
    }

    public int toDays() {
        return switch (unit) {
            case DAY -> count;
            case WEEK -> count * DAYS_PER_WEEK;
            case MONTH -> count * 30;
            case YEAR -> count * 365;
        };
    }
}
