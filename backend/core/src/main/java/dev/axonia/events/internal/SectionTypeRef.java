package dev.axonia.events.internal;

import org.springframework.data.relational.core.mapping.Table;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
@Table("event_series_section_type")
public class SectionTypeRef {

    private Long sectionTypeId;
    private String title;
    private Integer durationMinutes;
}
