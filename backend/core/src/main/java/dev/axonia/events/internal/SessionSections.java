package dev.axonia.events.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.axonia.NotFoundException;

/**
 * Pure structural helpers for navigating and rewriting the section/slot tree of a session.
 */
public final class SessionSections {

    private SessionSections() {
    }

    public static SectionEntity section(SessionEntity session, Long sectionId) {

        return nonNullSections(session.getSections()).stream().filter(s -> sectionId.equals(s.getId())).findFirst()
                .orElseThrow(() -> new NotFoundException("Section not found: " + sectionId));
    }

    public static SlotEntity slot(SectionEntity section, Long slotId) {

        return nonNullSlots(section.getSlots()).stream().filter(s -> slotId.equals(s.getId())).findFirst()
                .orElseThrow(() -> new NotFoundException("Slot not found: " + slotId));
    }

    public static Set<SlotPresenterRef> presenters(SlotEntity slot) {

        return Objects.requireNonNullElse(slot.getPresenters(), Set.of());
    }

    /**
     * The slot's own subject, as plain tags. Empty for a slot filled from a topic, which owns the subject instead.
     * Sorted, the canonical order {@link Tags#clean} already puts them in: the entity stores a {@code Set}, so any
     * other order would only be the one the database happened to hand back.
     */
    public static List<String> tags(SlotEntity slot) {

        return Objects.requireNonNullElse(slot.getTags(), Set.<SlotTagRef>of()).stream().map(SlotTagRef::getTag)
                .sorted().toList();
    }

    public static SessionEntity replaceSection(SessionEntity session, Long sectionId, SectionEntity updated) {

        var sections = new ArrayList<>(nonNullSections(session.getSections()));
        for (var i = 0; i < sections.size(); i++) {
            if (sectionId.equals(sections.get(i).getId())) {
                sections.set(i, updated);
            }
        }
        return session.toBuilder().sections(sections).build();
    }

    public static SectionEntity replaceSlot(SectionEntity section, Long slotId, SlotEntity updated) {

        var slots = new ArrayList<>(nonNullSlots(section.getSlots()));
        for (var i = 0; i < slots.size(); i++) {
            if (slotId.equals(slots.get(i).getId())) {
                slots.set(i, updated);
            }
        }
        return section.toBuilder().slots(slots).build();
    }

    public static List<SlotEntity> nonNullSlots(List<SlotEntity> slots) {

        return slots == null ? List.of() : slots;
    }

    public static List<SectionEntity> nonNullSections(List<SectionEntity> sections) {

        return sections == null ? List.of() : sections;
    }
}
