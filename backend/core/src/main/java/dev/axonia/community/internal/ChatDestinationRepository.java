package dev.axonia.community.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ChatDestinationRepository extends CrudRepository<ChatDestinationEntity, Long> {

    List<ChatDestinationEntity> findByOrganizationIdAndCommunityIdOrderByIdAsc(String organizationId, Long communityId);

    Optional<ChatDestinationEntity> findByOrganizationIdAndId(String organizationId, Long id);

    long countByOrganizationIdAndCommunityId(String organizationId, Long communityId);

    boolean existsByOrganizationIdAndCommunityIdAndName(String organizationId, Long communityId, String name);

    /**
     * Ids alone, for the drain's expansion: it needs somewhere to send, never the URLs, which it resolves one at a
     * time.
     */
    @Query("""
            SELECT id FROM community_chat_destination
            WHERE organization_id = :organizationId AND community_id = :communityId
            ORDER BY id
            """)
    List<Long> findIdsByOrganizationIdAndCommunityId(@Param("organizationId") String organizationId,
            @Param("communityId") Long communityId);
}
