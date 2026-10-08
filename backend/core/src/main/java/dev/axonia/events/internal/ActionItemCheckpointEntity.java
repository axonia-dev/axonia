package dev.axonia.events.internal;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import dev.axonia.events.CheckpointOutcome;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * One row per session-decided answer - the log behind an action item's {@code outcome}, which is state. An item closed
 * by ticking its own box has no session to attribute, so this table is written only from a checkpoint decision, never
 * from a direct close or drop. Carries no version or update timestamp: a decision, once logged, is never revised in
 * place.
 */
@Getter
@Builder
@ToString
@Table("action_item_checkpoint")
public class ActionItemCheckpointEntity {

    @Id
    private Long id;

    private String organizationId;
    private Long actionItemId;
    private Long sessionId;
    private CheckpointOutcome outcome;
    private String decidedByUserId;
    private Instant decidedAt;
}
