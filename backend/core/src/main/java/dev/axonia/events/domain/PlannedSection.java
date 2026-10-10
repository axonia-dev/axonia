package dev.axonia.events.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class PlannedSection {

    private Long sectionTypeId;
    private String title;
    private Integer durationMinutes;
}
