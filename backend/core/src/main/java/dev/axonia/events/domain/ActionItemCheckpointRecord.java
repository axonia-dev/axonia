package dev.axonia.events.domain;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class ActionItemCheckpointRecord {

    private Long id;
    private String organizationId;
    private Long actionItemId;
    private Long sessionId;
    private CheckpointOutcome outcome;
    private String decidedByUserId;
    private Instant decidedAt;
}
