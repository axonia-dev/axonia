package dev.axonia.mentoring.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import dev.axonia.shared.api.error.ValidationException;

/**
 * The calendar days a journal names: the day a meeting was held, the day of a planned one, and the day before it, when
 * each person is reminded. Every day is read in one person's own zone.
 */
public final class MeetingDays {

    /** When, on the day before the next meeting, each person is reminded, in their own zone. */
    public static final LocalTime REMINDER_TIME = LocalTime.of(9, 0);

    private MeetingDays() {
    }

    public static LocalDate dayOf(Instant at, ZoneId zone) {

        return at.atZone(zone).toLocalDate();
    }

    /** The day a note is about: today when none is given, else a day from the pairing's start to today. */
    public static LocalDate meetingDay(LocalDate requested, LocalDate startedOn, LocalDate today) {

        if (requested == null) {
            return today;
        }
        if (requested.isBefore(startedOn) || requested.isAfter(today)) {
            throw new ValidationException("MENTORING_MEETING_DAY",
                    "A meeting day is between the pairing's first day and today");
        }
        return requested;
    }

    /**
     * True from 09:00 on the day before the meeting, in the person's zone, and only for a date set before that day: a
     * date set for tomorrow or today is reminded of by nobody.
     */
    public static boolean reminderDue(LocalDate on, Instant setAt, Instant now, ZoneId zone) {

        var dayBefore = on.minusDays(1);
        var local = now.atZone(zone);
        return local.toLocalDate().equals(dayBefore) && !local.toLocalTime().isBefore(REMINDER_TIME)
                && dayOf(setAt, zone).isBefore(dayBefore);
    }
}
