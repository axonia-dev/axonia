package dev.axonia.events;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;

import lombok.Builder;

/**
 * {@code assignee} is an optional free-text reference to the member the work is dispatched to. Omitted or blank leaves
 * the item unassigned; supplied, it must resolve to exactly one member of the community. {@code checkpointSessionId}
 * re-pins or clears which session the item is answered at - omitted or null, it late-binds to whichever session comes
 * next. The origin session is set only at creation and is not editable.
 */
@Builder
public record UpdateActionItemRequest(@NotBlank String title, String description, List<String> tags, String assignee,
        LocalDate dueDate, Long checkpointSessionId) {
}
