package dev.axonia.events.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.application.port.out.TopicVoteStore;
import dev.axonia.events.domain.TopicVoteRecord;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcTopicVoteStore implements TopicVoteStore {

    private final TopicVoteRepository topicVoteRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<TopicVoteRecord> findByOrganizationIdAndTopicIdAndUserId(String organizationId, Long topicId,
            String userId) {

        return topicVoteRepository.findByOrganizationIdAndTopicIdAndUserId(organizationId, topicId, userId)
                .map(JdbcTopicVoteStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByOrganizationIdAndTopicId(String organizationId, Long topicId) {

        return topicVoteRepository.countByOrganizationIdAndTopicId(organizationId, topicId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByOrganizationIdAndTopicIdAndUserId(String organizationId, Long topicId, String userId) {

        return topicVoteRepository.existsByOrganizationIdAndTopicIdAndUserId(organizationId, topicId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicVoteRecord> findByOrganizationIdAndTopicIdIn(String organizationId, Collection<Long> topicIds) {

        return topicVoteRepository.findByOrganizationIdAndTopicIdIn(organizationId, topicIds).stream()
                .map(JdbcTopicVoteStore::toRecord).toList();
    }

    @Override
    @Transactional
    public TopicVoteRecord save(TopicVoteRecord vote) {

        return toRecord(topicVoteRepository.save(toEntity(vote)));
    }

    @Override
    public void delete(TopicVoteRecord vote) {

        topicVoteRepository.delete(toEntity(vote));
    }

    static TopicVoteRecord toRecord(TopicVoteEntity entity) {

        return TopicVoteRecord.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .topicId(entity.getTopicId()).userId(entity.getUserId()).createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt()).version(entity.getVersion()).build();
    }

    static TopicVoteEntity toEntity(TopicVoteRecord vote) {

        return TopicVoteEntity.builder().id(vote.getId()).organizationId(vote.getOrganizationId())
                .topicId(vote.getTopicId()).userId(vote.getUserId()).createdAt(vote.getCreatedAt())
                .updatedAt(vote.getUpdatedAt()).version(vote.getVersion()).build();
    }
}
