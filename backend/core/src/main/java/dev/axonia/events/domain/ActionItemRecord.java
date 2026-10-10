package dev.axonia.events.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import dev.axonia.events.api.ActionItemOutcome;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
public class ActionItemRecord {

    private Long id;
    private String organizationId;
    private Long communityId;
    private String title;
    private String description;
    private String raisedByUserId;
    private String assigneeUserId;
    private LocalDate dueDate;
    private Long originSessionId;
    private Long checkpointSessionId;
    private ActionItemOutcome outcome;
    private Set<ActionItemTag> tags;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
