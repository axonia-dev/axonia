package dev.axonia.events.adapter.out.persistence;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
@Table("event_section")
public class SectionEntity {

    @Id
    private Long id;

    private Long sectionTypeId;
    private String title;
    private Integer durationMinutes;
    private String notes;

    @MappedCollection(idColumn = "section_id", keyColumn = "position")
    private List<SlotEntity> slots;
}
