package dev.axonia.events.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import dev.axonia.events.api.SectionTemplate;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SeriesMother {

    public Series.SeriesBuilder any() {

        return Series.builder().id(1L).communityId(1L).title("Frontend Dojo")
                .cadence(new dev.axonia.events.domain.Cadence(1, CadenceUnit.WEEK))
                .startsOn(LocalDate.parse("2026-06-03")).preferredTime(LocalTime.of(18, 0)).timeZone("UTC")
                .durationMinutes(60).status(SeriesStatus.ACTIVE)
                .defaultStructure(List.of(new SectionTemplate(1L, null, null)));
    }
}
