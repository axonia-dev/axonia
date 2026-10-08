package dev.axonia.events;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;

import lombok.Builder;

/**
 * An action item raised in a community. {@code assignee} is an optional free-text reference to the member the work is
 * dispatched to - blank leaves it unassigned; supplied, it must resolve to exactly one member of the community.
 * {@code originSessionId} is the session it was raised at, history only. {@code checkpointSessionId} pins the session
 * it is answered at; omitted, it late-binds to whichever session comes next.
 */
@Builder
public record RaiseActionItemRequest(@NotBlank String title, String description, List<String> tags, String assignee,
        LocalDate dueDate, Long originSessionId, Long checkpointSessionId) {
}
