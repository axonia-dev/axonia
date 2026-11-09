package dev.axonia.events.domain;

/**
 * Which day a monthly cadence lands on in each month, read off the series' start date: the same day number ("on day
 * 13") or the same weekday at the same position ("on the second Wednesday", a fifth one reading as the last).
 */
public enum MonthlyRule {
    DAY_OF_MONTH, WEEKDAY_OF_MONTH
}
