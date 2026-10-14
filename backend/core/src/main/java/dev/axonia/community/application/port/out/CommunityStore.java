package dev.axonia.community.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.domain.CommunityRecord;
import dev.axonia.community.domain.KindCommunityCount;

public interface CommunityStore {

    Optional<CommunityRecord> findBySlug(String organizationId, String slug);

    Optional<CommunityRecord> findById(String organizationId, Long id);

    /** One organization's communities by id; an id with no community behind it is simply absent. */
    List<CommunityRecord> findByIds(String organizationId, Collection<Long> ids);

    boolean slugTaken(String organizationId, String slug);

    Page<CommunityRecord> searchByName(String organizationId, CommunityStatus status, String name, Pageable pageable);

    Page<CommunityRecord> search(CommunitySearch search, Pageable pageable);

    long countOfKind(String organizationId, Long kindId);

    void reassignKind(String organizationId, Long source, Long target);

    List<KindCommunityCount> countByKind(String organizationId);

    CommunityRecord save(CommunityRecord community);
}
