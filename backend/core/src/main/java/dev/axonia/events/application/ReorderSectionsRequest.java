package dev.axonia.events.application;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record ReorderSectionsRequest(@NotEmpty List<Long> sectionIds) {

    public ReorderSectionsRequest {
        sectionIds = sectionIds == null ? List.of() : List.copyOf(sectionIds);
    }
}
