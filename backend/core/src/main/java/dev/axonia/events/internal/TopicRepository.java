package dev.axonia.events.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface TopicRepository
        extends
            CrudRepository<TopicEntity, Long>,
            PagingAndSortingRepository<TopicEntity, Long>,
            TopicRepositoryCustom {

    Optional<TopicEntity> findByOrganizationIdAndId(String organizationId, Long id);

    List<TopicEntity> findByOrganizationIdAndIdIn(String organizationId, Collection<Long> ids);
}
