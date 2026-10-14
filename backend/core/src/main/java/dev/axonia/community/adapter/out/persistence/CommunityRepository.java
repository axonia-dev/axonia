package dev.axonia.community.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import dev.axonia.community.api.CommunityStatus;

public interface CommunityRepository
        extends
            CrudRepository<CommunityEntity, Long>,
            PagingAndSortingRepository<CommunityEntity, Long> {

    Optional<CommunityEntity> findByOrganizationIdAndSlug(String organizationId, String slug);

    Optional<CommunityEntity> findByOrganizationIdAndId(String organizationId, Long id);

    /** One organization's communities by id; an id with no row behind it is simply absent. */
    List<CommunityEntity> findByOrganizationIdAndIdIn(String organizationId, Collection<Long> ids);

    boolean existsByOrganizationIdAndSlug(String organizationId, String slug);

    Page<CommunityEntity> findByOrganizationIdAndStatusAndNameContainingIgnoreCase(String organizationId,
            CommunityStatus status, String name, Pageable pageable);

    Page<CommunityEntity> findByOrganizationIdAndStatusAndNameContainingIgnoreCaseAndIdIn(String organizationId,
            CommunityStatus status, String name, Collection<Long> ids, Pageable pageable);

    Page<CommunityEntity> findByOrganizationIdAndStatusAndNameContainingIgnoreCaseAndIdNotIn(String organizationId,
            CommunityStatus status, String name, Collection<Long> ids, Pageable pageable);

    /**
     * One organization's communities of a status, whole and ordered by name. Unpaged on purpose: an organization-wide
     * reader wants every one of them, and would immediately concatenate the pages a paged search handed it.
     */
    List<CommunityEntity> findByOrganizationIdAndStatusOrderByNameAsc(String organizationId, CommunityStatus status);

    long countByOrganizationIdAndKindId(String organizationId, Long kindId);

    @Modifying
    @Query("UPDATE communities SET kind_id = :target WHERE organization_id = :organizationId AND kind_id = :source")
    void reassignKind(@Param("organizationId") String organizationId, @Param("source") Long source,
            @Param("target") Long target);

    @Query("SELECT kind_id, COUNT(*) AS community_count FROM communities WHERE organization_id = :organizationId GROUP BY kind_id")
    List<KindCommunityCount> countCommunitiesByKind(@Param("organizationId") String organizationId);

    record KindCommunityCount(Long kindId, long communityCount) {
    }
}
