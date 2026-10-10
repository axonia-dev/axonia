package dev.axonia.events.adapter.out.persistence;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import dev.axonia.events.domain.CadenceUnit;
import dev.axonia.events.domain.SeriesStatus;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SeriesEntityMother {

    public SeriesEntity.SeriesEntityBuilder any() {

        return SeriesEntity.builder().organizationId("default").communityId(1L).title("Frontend Dojo").cadenceCount(1)
                .cadenceUnit(CadenceUnit.WEEK).startsOn(LocalDate.parse("2026-06-03"))
                .preferredTime(LocalTime.of(18, 0)).timeZone("UTC").status(SeriesStatus.ACTIVE)
                .defaultStructure(List.of());
    }
}
