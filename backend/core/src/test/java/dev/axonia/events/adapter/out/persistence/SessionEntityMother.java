package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SessionEntityMother {

    public SessionEntity.SessionEntityBuilder any() {

        return SessionEntity.builder().organizationId("default").seriesId(1L).communityId(1L).status("SCHEDULED")
                .scheduledAt(Instant.parse("2099-12-12T18:00:00Z")).hasTime(true)
                .occurrenceDate(LocalDate.of(2099, 12, 12)).title(null).notes("").recordingUrl("").sections(List.of());
    }

    public static SectionEntity section(Long sectionTypeId) {

        return SectionEntity.builder().sectionTypeId(sectionTypeId).title("Intro").notes(null).slots(List.of(slot()))
                .build();
    }

    public static SlotEntity slot() {

        return SlotEntity.builder().title("Intro talk").notes(null).durationMinutes(15)
                .presenters(Set.of(SlotPresenterRef.builder().userId("dev-user").build())).build();
    }
}
