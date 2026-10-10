package dev.axonia.events.domain;

import java.util.Set;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
public class SlotRecord {

    private Long id;
    private Long topicId;
    private String title;
    private String notes;
    private Integer durationMinutes;
    private Set<SlotPresenter> presenters;

    /** The slot's own subject, for a slot that was never proposed. Empty when {@code topicId} is set: never both. */
    private Set<SlotTag> tags;
}
