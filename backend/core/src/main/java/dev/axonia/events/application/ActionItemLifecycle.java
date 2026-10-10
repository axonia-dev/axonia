package dev.axonia.events.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.ActionItemOutcome;
import dev.axonia.events.application.port.out.ActionItemCheckpointStore;
import dev.axonia.events.application.port.out.ActionItemStore;
import dev.axonia.events.domain.ActionItemCheckpointRecord;
import dev.axonia.events.domain.ActionItemRecord;
import lombok.RequiredArgsConstructor;

/**
 * Applies one checkpoint decision made at a session's follow-up pass. The caller is the organizer closing out the
 * session, who is not necessarily the assignee or a topic manager of any item being decided here, so this writes
 * through the repositories directly rather than through {@code ActionItemService.close}/{@code drop}, which authorize
 * against the assignee-or-manager rule.
 */
@Component
@RequiredArgsConstructor
public class ActionItemLifecycle {

    private final ActionItemStore actionItemStore;
    private final ActionItemCheckpointStore actionItemCheckpointStore;
    private final ActionItemCloser actionItemCloser;

    @Transactional
    public ActionItemRecord decide(CheckpointDecisionCommand command) {

        var item = command.item();
        actionItemCheckpointStore.save(ActionItemCheckpointRecord.builder().organizationId(item.getOrganizationId())
                .actionItemId(item.getId()).sessionId(command.sessionId()).outcome(command.outcome())
                .decidedByUserId(command.decidedByUserId()).decidedAt(command.decidedAt()).build());

        // A switch EXPRESSION, not a switch statement: the compiler requires every CheckpointOutcome to be covered,
        // with no default arm to quietly absorb one that is not. A statement form would compile just as happily with
        // a case missing, silently logging the checkpoint row above and changing no state for it.
        return switch (command.outcome()) {
            case DONE -> actionItemCloser.closeAndCredit(item);
            case CARRIED -> actionItemStore
                    .save(item.toBuilder().outcome(ActionItemOutcome.OPEN).checkpointSessionId(null).build());
            case DROPPED -> actionItemStore.save(item.toBuilder().outcome(ActionItemOutcome.DROPPED).build());
        };
    }
}
