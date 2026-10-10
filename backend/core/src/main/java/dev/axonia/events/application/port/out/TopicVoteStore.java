package dev.axonia.events.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import dev.axonia.events.domain.TopicVoteRecord;

public interface TopicVoteStore {

    Optional<TopicVoteRecord> findByOrganizationIdAndTopicIdAndUserId(String organizationId, Long topicId,
            String userId);

    long countByOrganizationIdAndTopicId(String organizationId, Long topicId);

    boolean existsByOrganizationIdAndTopicIdAndUserId(String organizationId, Long topicId, String userId);

    List<TopicVoteRecord> findByOrganizationIdAndTopicIdIn(String organizationId, Collection<Long> topicIds);

    TopicVoteRecord save(TopicVoteRecord vote);

    void delete(TopicVoteRecord vote);
}
