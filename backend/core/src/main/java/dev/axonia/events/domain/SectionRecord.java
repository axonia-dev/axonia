package dev.axonia.events.domain;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
public class SectionRecord {

    private Long id;
    private Long sectionTypeId;
    private String title;
    private Integer durationMinutes;
    private String notes;
    private List<SlotRecord> slots;
}
