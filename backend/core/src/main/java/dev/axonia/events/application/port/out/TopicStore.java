package dev.axonia.events.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import dev.axonia.events.application.TopicFilter;
import dev.axonia.events.domain.TopicCounts;
import dev.axonia.events.domain.TopicRecord;
import dev.axonia.events.domain.TopicSchedule;

public interface TopicStore {

    Optional<TopicRecord> findByOrganizationIdAndId(String organizationId, Long id);

    /** The topic, locked until the transaction ends, so two writes to its speakers apply one after the other. */
    Optional<TopicRecord> findForUpdate(String organizationId, Long id);

    Page<TopicRecord> findFiltered(String organizationId, Long communityId, TopicFilter filter, Pageable pageable);

    Page<TopicRecord> findPast(String organizationId, Long communityId, String q, Pageable pageable);

    /**
     * The speakers of each topic, sorted and distinct: its backlog rows while {@code PROPOSED}, else its slot's
     * presenters on sessions that are not cancelled. A topic with no speaker has no key.
     */
    Map<Long, List<String>> findSpeakers(String organizationId, Collection<Long> topicIds);

    List<TopicSchedule> findSchedules(String organizationId, Collection<Long> topicIds);

    TopicCounts countByLifecycle(String organizationId, Long communityId);

    TopicRecord save(TopicRecord topic);

    void delete(TopicRecord topic);
}
