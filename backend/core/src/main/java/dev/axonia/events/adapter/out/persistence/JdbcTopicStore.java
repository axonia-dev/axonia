package dev.axonia.events.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.application.TopicFilter;
import dev.axonia.events.application.port.out.TopicStore;
import dev.axonia.events.domain.TopicCounts;
import dev.axonia.events.domain.TopicRecord;
import dev.axonia.events.domain.TopicSchedule;
import dev.axonia.events.domain.TopicTag;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcTopicStore implements TopicStore {

    private final TopicRepository topicRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<TopicRecord> findByOrganizationIdAndId(String organizationId, Long id) {

        return topicRepository.findByOrganizationIdAndId(organizationId, id).map(JdbcTopicStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TopicRecord> findFiltered(String organizationId, Long communityId, TopicFilter filter,
            Pageable pageable) {

        return topicRepository.findFiltered(organizationId, communityId, filter, pageable)
                .map(JdbcTopicStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TopicRecord> findPast(String organizationId, Long communityId, String q, Pageable pageable) {

        return topicRepository.findPast(organizationId, communityId, q, pageable).map(JdbcTopicStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicSchedule> findSchedules(String organizationId, Collection<Long> topicIds) {

        return topicRepository.findSchedules(organizationId, topicIds);
    }

    @Override
    @Transactional(readOnly = true)
    public TopicCounts countByLifecycle(String organizationId, Long communityId) {

        return topicRepository.countByLifecycle(organizationId, communityId);
    }

    @Override
    @Transactional
    public TopicRecord save(TopicRecord topic) {

        return toRecord(topicRepository.save(toEntity(topic)));
    }

    @Override
    public void delete(TopicRecord topic) {

        topicRepository.delete(toEntity(topic));
    }

    static TopicRecord toRecord(TopicEntity entity) {

        return TopicRecord.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .communityId(entity.getCommunityId()).kind(entity.getKind()).title(entity.getTitle())
                .description(entity.getDescription()).proposerUserId(entity.getProposerUserId())
                .status(entity.getStatus())
                .tags(RecordMapping.set(entity.getTags(), t -> TopicTag.builder().tag(t.getTag()).build()))
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt()).version(entity.getVersion()).build();
    }

    static TopicEntity toEntity(TopicRecord topic) {

        return TopicEntity.builder().id(topic.getId()).organizationId(topic.getOrganizationId())
                .communityId(topic.getCommunityId()).kind(topic.getKind()).title(topic.getTitle())
                .description(topic.getDescription()).proposerUserId(topic.getProposerUserId()).status(topic.getStatus())
                .tags(RecordMapping.set(topic.getTags(), t -> TopicTagRef.builder().tag(t.getTag()).build()))
                .createdAt(topic.getCreatedAt()).updatedAt(topic.getUpdatedAt()).version(topic.getVersion()).build();
    }
}
