package dev.axonia.events.application;

import dev.axonia.events.api.ScheduleEntry;
import dev.axonia.events.api.Session;
import lombok.experimental.UtilityClass;

/** A real session as the schedule shows it: the one mapping the month view and the memories share. */
@UtilityClass
class ScheduleEntries {

    ScheduleEntry of(Session session) {

        return ScheduleEntry.builder().sessionId(session.id()).seriesId(session.seriesId()).title(session.title())
                .displayTitle(session.displayTitle()).occurrenceDate(session.occurrenceDate())
                .scheduledAt(session.scheduledAt()).hasTime(session.hasTime()).seriesTimeZone(session.seriesTimeZone())
                .durationMinutes(session.durationMinutes()).status(session.status()).place(session.place())
                .version(session.version()).updatedAt(session.updatedAt()).virtual(false).sections(session.sections())
                .build();
    }
}
