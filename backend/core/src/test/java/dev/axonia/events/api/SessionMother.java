package dev.axonia.events.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SessionMother {

    public Session.SessionBuilder any() {

        return Session.builder().id(7L).seriesId(1L).communityId(1L).title(null)
                .occurrenceDate(LocalDate.of(2099, 12, 12)).status(SessionStatus.SCHEDULED)
                .scheduledAt(Instant.parse("2099-12-12T18:00:00Z")).hasTime(true).durationMinutes(60).attendeeCount(0L)
                .notes("").recordingUrl("")
                .sections(List.of(Section.builder().id(21L).sessionId(7L).position(0).sectionTypeId(1L)
                        .sectionType("Talk").title("Intro").slots(List.of(Slot.builder().id(11L).sectionId(21L)
                                .position(0).presenterUserIds(List.of("dev-user")).title("Intro talk").build()))
                        .build()));
    }

    public Session.SessionBuilder aHeldSession() {

        return any().status(SessionStatus.HELD).closedAt(Instant.parse("2099-12-12T19:00:00Z"));
    }
}
