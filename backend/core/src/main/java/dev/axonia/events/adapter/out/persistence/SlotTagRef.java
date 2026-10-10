package dev.axonia.events.adapter.out.persistence;

import org.springframework.data.relational.core.mapping.Table;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * One tag of a slot's own subject, for a slot that was never proposed as a topic. Mirrors {@link TopicTagRef}: a slot
 * filled from a topic takes its subject from the topic and carries none of these.
 */
@Getter
@Builder
@ToString
@Table("event_slot_tag")
public class SlotTagRef {

    private String tag;
}
