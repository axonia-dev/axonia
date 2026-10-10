package dev.axonia.events.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SessionRecordMother {

    public SessionRecord.SessionRecordBuilder any() {

        return SessionRecord.builder().organizationId("default").seriesId(1L).communityId(1L).status("SCHEDULED")
                .scheduledAt(Instant.parse("2099-12-12T18:00:00Z")).hasTime(true)
                .occurrenceDate(LocalDate.of(2099, 12, 12)).title(null).notes("").recordingUrl("").sections(List.of());
    }

    public static SectionRecord section(Long sectionTypeId) {

        return SectionRecord.builder().sectionTypeId(sectionTypeId).title("Intro").notes(null).slots(List.of(slot()))
                .build();
    }

    public static SlotRecord slot() {

        return SlotRecord.builder().title("Intro talk").notes(null).durationMinutes(15)
                .presenters(Set.of(SlotPresenter.builder().userId("dev-user").build())).build();
    }
}
