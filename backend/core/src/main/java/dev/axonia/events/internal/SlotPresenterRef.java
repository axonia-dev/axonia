package dev.axonia.events.internal;

import org.springframework.data.relational.core.mapping.Table;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
@Table("event_slot_presenter")
public class SlotPresenterRef {

    private String userId;
}
