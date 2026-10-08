package dev.axonia.events.internal;

import java.time.LocalDate;

import dev.axonia.events.ActionItemOutcome;

/**
 * Everything {@link SessionFacts} needs to render one action point line in the wrap-up's "What we agreed to do"
 * section, before {@code assigneeUserId} becomes a resolved name and before Thymeleaf decides how {@code dueDate} is
 * formatted. {@link SessionFactsAssembler} builds one of these per {@link ActionItemEntity} that
 * {@code ActionItemRepository#findByOriginSessionId} returns for the session being wrapped up - what this session
 * raised, not what it checked (see {@link CheckedItemSource} for that half).
 *
 * <p>
 * {@code assigneeUserId} travels as a raw id, the same way {@link dev.axonia.events.Slot#presenterUserIds()} does: it
 * is resolved to a display name inside {@link SessionFacts}, against the same {@code displayNamesByUserId} map
 * presenters share, and dropped rather than defaulted to the id when the map has no entry for it. That is a deliberate
 * departure from {@link ActionItemReadModel}, which defaults an unresolved assignee to its own id for the backlog
 * screen - a raw id is tolerable on a page a member is already looking at, never in a message landing in their inbox.
 *
 * <p>
 * {@code outcome} carries the full three-state {@link ActionItemOutcome}, not a {@code done} boolean: that boolean was
 * lossless against the {@code TopicStatus} this class used to be built from (which had no dropped state), and became
 * lossy the moment this aggregate introduced one. {@link SessionFacts} derives both its {@code done} and
 * {@code dropped} facts from this one field, so a dropped item can never again render identically to an open one.
 */
public record ActionPointSource(String title, String assigneeUserId, LocalDate dueDate, ActionItemOutcome outcome) {
}
