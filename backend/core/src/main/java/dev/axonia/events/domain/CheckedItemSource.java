package dev.axonia.events.domain;

/**
 * Everything {@link SessionFacts} needs to render one line in the wrap-up's "What was checked" section: an item this
 * session answered, alongside {@link ActionPointSource} for what it raised. {@code SessionFactsAssembler} builds one of
 * these per {@link ActionItemCheckpointRecord} that
 * {@code ActionItemCheckpointStore#findByOrganizationIdAndCommunityIdAndSessionId} returns for the session being
 * wrapped up, joined by hand against the {@link ActionItemRecord} it decided on: the checkpoint row itself carries
 * neither a title nor an assignee, only the decision.
 *
 * <p>
 * {@code assigneeUserId} travels as a raw id and is resolved the exact same way
 * {@link ActionPointSource#assigneeUserId()} is - against the same {@code displayNamesByUserId} map, inside
 * {@link SessionFacts}, dropped rather than rendered raw when unresolved.
 *
 * <p>
 * {@code outcome} is the decision logged AT this session, never the item's own current state: a {@code CARRIED} item
 * went back onto the backlog for a later session to answer, but this line still reports what this session decided.
 */
public record CheckedItemSource(String title, String assigneeUserId, CheckpointOutcome outcome) {
}
