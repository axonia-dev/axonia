package dev.axonia.events;

import java.util.List;

import lombok.Builder;

@Builder
public record Section(Long id, Long sessionId, Integer position, Long sectionTypeId, String sectionType,
        String sectionTypeIcon, String sectionTypeAccentColor, String title, Integer durationMinutes,
        List<Slot> slots) {

    public Section {
        slots = slots == null ? List.of() : List.copyOf(slots);
    }
}
