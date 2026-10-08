package dev.axonia.events.internal;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ConflictException;
import dev.axonia.events.CheckpointDecision;
import lombok.RequiredArgsConstructor;

/**
 * What a session's follow-up pass does with the list of decisions it was handed: refuse a repeated item up front,
 * resolve each one against the items this session actually owes an answer on, and apply it. {@link ActionItemLifecycle}
 * owns what a single decision does; this owns what the whole pass does, so {@code SessionService.recordFollowUp} keeps
 * its own responsibilities - notes, recording URL, the topic-DONE loop, authorization - and calls this once.
 */
@Component
@RequiredArgsConstructor
public class CheckpointDecisionPass {

    private final ActionItemRepository actionItemRepository;
    private final ActionItemLifecycle actionItemLifecycle;

    @Transactional
    public void apply(SessionEntity session, List<CheckpointDecision> decisions, String decidedByUserId,
            Instant decidedAt) {

        requireNoDuplicateDecisions(decisions);
        var open = actionItemRepository
                .findOpenByCheckpointSession(session.getOrganizationId(), session.getCommunityId(), session.getId())
                .stream().collect(Collectors.toMap(ActionItemEntity::getId, Function.identity()));
        for (var decision : decisions) {
            var item = open.get(decision.actionItemId());
            if (item == null) {
                throw new ConflictException("NOT_PINNED_HERE", "That action item is not answered at this session");
            }
            actionItemLifecycle.decide(CheckpointDecisionCommand.builder().item(item).sessionId(session.getId())
                    .outcome(decision.outcome()).decidedByUserId(decidedByUserId).decidedAt(decidedAt).build());
        }
    }

    /**
     * {@code decisions} is a user-supplied array: naming the same item twice would reuse the same in-memory entity
     * snapshot for both saves, so the second write would carry a stale {@code @Version} and fail with an optimistic
     * lock exception instead of a clean refusal.
     */
    private void requireNoDuplicateDecisions(List<CheckpointDecision> decisions) {

        var distinctCount = decisions.stream().map(CheckpointDecision::actionItemId).distinct().count();
        if (distinctCount != decisions.size()) {
            throw new ConflictException("DUPLICATE_DECISION", "Each action item may appear once in a follow-up pass");
        }
    }
}
