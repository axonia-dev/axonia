package dev.axonia.events.internal;

import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
@Table("event_slot")
public class SlotEntity {

    @Id
    private Long id;

    private Long topicId;
    private String title;
    private String notes;
    private Integer durationMinutes;

    @MappedCollection(idColumn = "slot_id")
    private Set<SlotPresenterRef> presenters;

    /** The slot's own subject, for a slot that was never proposed. Empty when {@code topicId} is set: never both. */
    @MappedCollection(idColumn = "slot_id")
    private Set<SlotTagRef> tags;
}
