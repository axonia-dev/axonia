package dev.axonia.events.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SeriesRecordMother {

    public SeriesRecord.SeriesRecordBuilder any() {

        return SeriesRecord.builder().organizationId("default").communityId(1L).title("Frontend Dojo").cadenceCount(1)
                .cadenceUnit(CadenceUnit.WEEK).startsOn(LocalDate.parse("2026-06-03"))
                .preferredTime(LocalTime.of(18, 0)).timeZone("UTC").status(SeriesStatus.ACTIVE)
                .defaultStructure(List.of());
    }
}
