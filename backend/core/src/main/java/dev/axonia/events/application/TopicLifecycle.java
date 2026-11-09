package dev.axonia.events.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.TopicStatus;
import dev.axonia.events.application.port.out.PresentPeople;
import dev.axonia.events.application.port.out.TopicStore;
import dev.axonia.events.domain.SlotPresenter;
import dev.axonia.events.domain.SlotRecord;
import dev.axonia.events.domain.TopicRecord;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Owns the status transitions of a topic as it moves through the session lifecycle, and its speakers with it: a backlog
 * topic becomes SCHEDULED when pulled into a slot, handing its speakers to that slot as presenters, and reverts to
 * PROPOSED when it loses the slot (unless it is already DONE), taking the slot's presenters back as its speakers.
 */
@Component
@RequiredArgsConstructor
public class TopicLifecycle {

    private final TopicStore topicStore;
    private final TopicEvidence topicEvidence;
    private final PresentPeople presentPeople;

    /** A topic just put on the schedule, with the backlog speakers its slot now carries as presenters. */
    public record ScheduledTopic(TopicRecord topic, Set<String> speakerUserIds) {

        public ScheduledTopic {
            speakerUserIds = Set.copyOf(speakerUserIds);
        }
    }

    @Transactional
    public ScheduledTopic schedule(String organizationId, Long communityId, Long topicId) {

        var topic = topicStore.findByOrganizationIdAndId(organizationId, topicId)
                .orElseThrow(() -> new NotFoundException("Topic not found: " + topicId));
        if (!topic.getCommunityId().equals(communityId)) {
            throw new ConflictException("TOPIC_COMMUNITY_MISMATCH", "Topic belongs to a different community");
        }
        if (topic.getStatus() != TopicStatus.PROPOSED) {
            throw new ConflictException("NOT_PROPOSED", "This topic is no longer on the backlog");
        }
        var speakers = Set.copyOf(topic.speakerUserIds());
        var saved = topicStore.save(topic.toBuilder().status(TopicStatus.SCHEDULED).build().withSpeakers(List.of()));
        return new ScheduledTopic(saved, speakers);
    }

    /** No-op when the slot carries no topic, or it is gone or already DONE. */
    public void release(String organizationId, SlotRecord slot) {

        releaseAll(organizationId, List.of(slot));
    }

    /**
     * Releases every topic these slots carry, each once: a topic on two slots of one session returns with the
     * presenters of both.
     */
    public void releaseAll(String organizationId, Collection<SlotRecord> slots) {

        slots.stream().filter(slot -> slot.getTopicId() != null).collect(Collectors.groupingBy(SlotRecord::getTopicId,
                LinkedHashMap::new,
                Collectors.flatMapping(slot -> SessionSections.presenters(slot).stream().map(SlotPresenter::getUserId),
                        Collectors.toList())))
                .forEach((topicId, presenters) -> restore(organizationId, topicId, presenters));
    }

    private void restore(String organizationId, Long topicId, List<String> presenters) {

        topicStore.findByOrganizationIdAndId(organizationId, topicId).filter(t -> t.getStatus() != TopicStatus.DONE)
                .ifPresent(t -> {
                    var saved = topicStore.save(t.toBuilder().status(TopicStatus.PROPOSED).build()
                            .withSpeakers(peopleAmong(organizationId, presenters)));
                    topicEvidence.publish(saved, saved.speakerUserIds());
                });
    }

    /**
     * A slot keeps who was due to present it as history, but only people still in the organization stand behind it as a
     * commitment: an erased surrogate or a deprovisioned member does not come back to the backlog as a speaker.
     */
    private List<String> peopleAmong(String organizationId, List<String> presenters) {

        var present = presentPeople.presentAmong(organizationId, presenters);
        return presenters.stream().filter(present::contains).toList();
    }

    /** When an edit detaches or swaps a slot's topic, release the one it carried before, with its presenters. */
    public void releaseReplaced(String organizationId, SlotRecord previousSlot, Long newTopicId) {

        if (previousSlot.getTopicId() != null && !previousSlot.getTopicId().equals(newTopicId)) {
            release(organizationId, previousSlot);
        }
    }
}
