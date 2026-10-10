package dev.axonia.events.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

public interface TopicVoteRepository extends CrudRepository<TopicVoteEntity, Long> {

    Optional<TopicVoteEntity> findByOrganizationIdAndTopicIdAndUserId(String organizationId, Long topicId,
            String userId);

    long countByOrganizationIdAndTopicId(String organizationId, Long topicId);

    boolean existsByOrganizationIdAndTopicIdAndUserId(String organizationId, Long topicId, String userId);

    List<TopicVoteEntity> findByOrganizationIdAndTopicIdIn(String organizationId, Collection<Long> topicIds);
}
