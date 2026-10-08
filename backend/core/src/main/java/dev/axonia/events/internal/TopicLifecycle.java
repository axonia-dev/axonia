package dev.axonia.events.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ConflictException;
import dev.axonia.NotFoundException;
import dev.axonia.events.TopicStatus;
import lombok.RequiredArgsConstructor;

// Owns the status transitions of a Topic as it moves through the session lifecycle:
// a backlog topic becomes SCHEDULED when pulled into a slot, and reverts to PROPOSED
// when it loses that slot (unless it has already been marked DONE).
@Component
@RequiredArgsConstructor
public class TopicLifecycle {

    private final TopicRepository topicRepository;

    // Find the topic, guard it belongs to the community, and mark it SCHEDULED. Returns the topic.
    @Transactional
    public TopicEntity schedule(String organizationId, Long communityId, Long topicId) {

        var topic = topicRepository.findByOrganizationIdAndId(organizationId, topicId)
                .orElseThrow(() -> new NotFoundException("Topic not found: " + topicId));
        if (!topic.getCommunityId().equals(communityId)) {
            throw new ConflictException("TOPIC_COMMUNITY_MISMATCH", "Topic belongs to a different community");
        }
        return topicRepository.save(topic.toBuilder().status(TopicStatus.SCHEDULED).build());
    }

    // Revert a topic to PROPOSED when it loses its slot. No-op when absent, already DONE, or null.
    public void release(String organizationId, Long topicId) {

        if (topicId == null) {
            return;
        }
        topicRepository.findByOrganizationIdAndId(organizationId, topicId)
                .filter(t -> t.getStatus() != TopicStatus.DONE)
                .ifPresent(t -> topicRepository.save(t.toBuilder().status(TopicStatus.PROPOSED).build()));
    }

    // When an edit detaches or swaps a slot's topic, release the one it carried before.
    public void releaseReplaced(String organizationId, Long previousTopicId, Long newTopicId) {

        if (previousTopicId != null && !previousTopicId.equals(newTopicId)) {
            release(organizationId, previousTopicId);
        }
    }
}
