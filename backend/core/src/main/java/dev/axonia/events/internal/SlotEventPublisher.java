package dev.axonia.events.internal;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import dev.axonia.events.SlotCleared;
import dev.axonia.events.SlotPresented;
import lombok.RequiredArgsConstructor;

/**
 * Publishes the SlotPresented/SlotCleared domain event a slot's presenter, topic or tag assignment causes. A slot only
 * "presents" once it has at least one presenter and a subject that resolves to at least one tag; anything short of that
 * clears the slot, so every caller that changes a slot shares the same rule instead of re-deriving it.
 *
 * <p>
 * The subject comes from the topic when the slot was filled from one, and from the slot's own tags otherwise. The
 * topic's tags are read here, at publish time, rather than copied onto the slot: the slot therefore never holds a stale
 * copy of a subject that has since moved, and every event it publishes carries the topic's tags as they are now.
 *
 * <p>
 * That is not propagation, and it should not be read as any. Nothing republishes a slot when its topic is re-tagged, so
 * re-tagging a backlog topic does not by itself reach the expertise graph: the corrected subject lands the next time
 * something writes to the slot that was filled from it. Closing that gap is a separate design call.
 */
@Component
@RequiredArgsConstructor
public class SlotEventPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final TopicRepository topicRepository;

    public void publish(SlotEventCoords coords, List<String> presenterIds, Long topicId, List<String> slotTags) {

        if (presenterIds.isEmpty()) {
            eventPublisher.publishEvent(new SlotCleared(coords.slotId(), coords.communityId()));
            return;
        }
        var tags = topicId != null ? resolveTopicTags(coords.organizationId(), topicId) : List.copyOf(slotTags);
        if (tags.isEmpty()) {
            eventPublisher.publishEvent(new SlotCleared(coords.slotId(), coords.communityId()));
        } else {
            eventPublisher.publishEvent(
                    new SlotPresented(coords.slotId(), coords.communityId(), List.copyOf(presenterIds), tags));
        }
    }

    private List<String> resolveTopicTags(String organizationId, Long topicId) {

        return topicRepository.findByOrganizationIdAndId(organizationId, topicId).map(
                t -> t.getTags() == null ? List.<String>of() : t.getTags().stream().map(ref -> ref.getTag()).toList())
                .orElse(List.of());
    }
}
