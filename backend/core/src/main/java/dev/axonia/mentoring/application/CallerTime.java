package dev.axonia.mentoring.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import dev.axonia.mentoring.domain.MeetingDays;

/** The moment a member reads at, and their own zone, in which their calendar days are read. */
record CallerTime(Instant now, ZoneId zone) {

    LocalDate today() {

        return MeetingDays.dayOf(now, zone);
    }
}
