package dev.axonia.events.internal;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.ActionItemClosed;
import dev.axonia.events.ActionItemOutcome;
import lombok.RequiredArgsConstructor;

/**
 * The one place an action item is marked {@code DONE} and its assignee credited. {@code ActionItemService.close} and
 * {@code ActionItemLifecycle}'s {@code DONE} branch both call this rather than each saving the entity and publishing
 * {@link ActionItemClosed} on their own, so the two call sites cannot drift apart. Both callers are themselves
 * {@code @Transactional}, so this joins their transaction; it carries its own annotation because it is a public method
 * calling a repository and the read-path transaction guard requires that on every one, not just the ones happening to
 * already run inside a caller's transaction today.
 */
@Component
@RequiredArgsConstructor
public class ActionItemCloser {

    private final ActionItemRepository actionItemRepository;
    private final ActionItemMapper actionItemMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ActionItemEntity closeAndCredit(ActionItemEntity item) {

        var saved = actionItemRepository.save(item.toBuilder().outcome(ActionItemOutcome.DONE).build());
        eventPublisher.publishEvent(new ActionItemClosed(saved.getId(), saved.getCommunityId(),
                saved.getAssigneeUserId(), actionItemMapper.tags(saved)));
        return saved;
    }
}
