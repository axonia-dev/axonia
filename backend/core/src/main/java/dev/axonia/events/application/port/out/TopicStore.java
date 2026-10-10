package dev.axonia.events.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import dev.axonia.events.application.TopicFilter;
import dev.axonia.events.domain.TopicCounts;
import dev.axonia.events.domain.TopicRecord;
import dev.axonia.events.domain.TopicSchedule;

public interface TopicStore {

    Optional<TopicRecord> findByOrganizationIdAndId(String organizationId, Long id);

    Page<TopicRecord> findFiltered(String organizationId, Long communityId, TopicFilter filter, Pageable pageable);

    Page<TopicRecord> findPast(String organizationId, Long communityId, String q, Pageable pageable);

    List<TopicSchedule> findSchedules(String organizationId, Collection<Long> topicIds);

    TopicCounts countByLifecycle(String organizationId, Long communityId);

    TopicRecord save(TopicRecord topic);

    void delete(TopicRecord topic);
}
