package dev.axonia.events.internal;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import dev.axonia.events.TopicCounts;

interface TopicRepositoryCustom {

    Page<TopicEntity> findFiltered(String organizationId, Long communityId, TopicFilter filter, Pageable pageable);

    List<TopicSchedule> findSchedules(String organizationId, Collection<Long> topicIds);

    Page<TopicEntity> findPast(String organizationId, Long communityId, String q, Pageable pageable);

    TopicCounts countByLifecycle(String organizationId, Long communityId);
}
