package dev.axonia.events.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

import dev.axonia.events.application.SessionMapper;

public interface SeriesRepository
        extends
            CrudRepository<SeriesEntity, Long>,
            PagingAndSortingRepository<SeriesEntity, Long> {

    Optional<SeriesEntity> findByOrganizationIdAndId(String organizationId, Long id);

    /** Every named series in one read, so {@link SessionMapper#toSessions} resolves a batch's standing rooms once. */
    List<SeriesEntity> findByOrganizationIdAndIdIn(String organizationId, Collection<Long> ids);

    List<SeriesEntity> findByOrganizationIdAndCommunityId(String organizationId, Long communityId);

    Page<SeriesEntity> findByOrganizationIdAndCommunityId(String organizationId, Long communityId, Pageable pageable);
}
