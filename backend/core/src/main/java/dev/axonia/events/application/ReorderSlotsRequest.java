package dev.axonia.events.application;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record ReorderSlotsRequest(@NotEmpty List<Long> slotIds) {

    public ReorderSlotsRequest {
        slotIds = slotIds == null ? List.of() : List.copyOf(slotIds);
    }
}
