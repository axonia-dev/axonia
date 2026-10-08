package dev.axonia.events.mother;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import dev.axonia.events.CadenceUnit;
import dev.axonia.events.SectionTemplate;
import dev.axonia.events.Series;
import dev.axonia.events.SeriesStatus;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SeriesMother {

    public Series.SeriesBuilder any() {

        return Series.builder().id(1L).communityId(1L).title("Frontend Dojo")
                .cadence(new dev.axonia.events.Cadence(1, CadenceUnit.WEEK)).startsOn(LocalDate.parse("2026-06-03"))
                .preferredTime(LocalTime.of(18, 0)).timeZone("UTC").durationMinutes(60).status(SeriesStatus.ACTIVE)
                .defaultStructure(List.of(new SectionTemplate(1L, null, null)));
    }
}
