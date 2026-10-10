package dev.axonia.events.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import dev.axonia.events.domain.SeriesRecord;

public interface SeriesStore {

    Optional<SeriesRecord> findByOrganizationIdAndId(String organizationId, Long id);

    List<SeriesRecord> findByOrganizationIdAndIdIn(String organizationId, Collection<Long> ids);

    List<SeriesRecord> findByOrganizationIdAndCommunityId(String organizationId, Long communityId);

    Page<SeriesRecord> findByOrganizationIdAndCommunityId(String organizationId, Long communityId, Pageable pageable);

    SeriesRecord save(SeriesRecord series);
}
