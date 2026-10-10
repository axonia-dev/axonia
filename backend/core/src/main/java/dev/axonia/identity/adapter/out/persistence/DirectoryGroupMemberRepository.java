package dev.axonia.identity.adapter.out.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface DirectoryGroupMemberRepository extends CrudRepository<DirectoryGroupMemberEntity, Long> {

    List<DirectoryGroupMemberEntity> findByOrganizationIdAndGroupId(String organizationId, Long groupId);

    List<DirectoryGroupMemberEntity> findByOrganizationIdAndUserScimId(String organizationId, String userScimId);

    /**
     * The distinct signed-in users among the members of the given groups. A member whose {@code identity_user.user_id}
     * is null has never signed in and contributes nobody: joining on it rather than filtering afterwards keeps this as
     * cheap as {@link DirectoryGroupRepository#findByOrganizationIdAndMemberScimId} keeps the reverse lookup.
     */
    @Query("""
            SELECT DISTINCT u.user_id FROM directory_group_member m
            JOIN identity_user u ON u.organization_id = m.organization_id AND u.scim_id = m.user_scim_id
            WHERE m.organization_id = :organizationId AND m.group_id IN (:groupIds) AND u.user_id IS NOT NULL
            """)
    List<String> findUserIdsByOrganizationIdAndGroupIdIn(@Param("organizationId") String organizationId,
            @Param("groupIds") Collection<Long> groupIds);

    /**
     * Race-safe idempotent insert: duplicate (organization_id, group_id, user_scim_id) is silently ignored so that
     * re-sending the same member during an IdP full-sync cycle does not produce a DataIntegrityViolationException.
     * Audit columns are set in SQL because Spring Data auditing cannot populate a raw-query insert.
     */
    @Modifying
    @Query("""
            INSERT INTO directory_group_member (organization_id, group_id, user_scim_id, created_at, updated_at, version)
            VALUES (:organizationId, :groupId, :userScimId, now(), now(), 0)
            ON CONFLICT (organization_id, group_id, user_scim_id) DO NOTHING
            """)
    void insertIgnoreDuplicate(@Param("organizationId") String organizationId, @Param("groupId") Long groupId,
            @Param("userScimId") String userScimId);

    @Modifying
    @Query("DELETE FROM directory_group_member WHERE organization_id = :organizationId AND group_id = :groupId AND user_scim_id = :userScimId")
    void deleteByOrganizationIdAndGroupIdAndUserScimId(@Param("organizationId") String organizationId,
            @Param("groupId") Long groupId, @Param("userScimId") String userScimId);

    @Modifying
    @Query("DELETE FROM directory_group_member WHERE organization_id = :organizationId AND group_id = :groupId")
    void deleteAllByOrganizationIdAndGroupId(@Param("organizationId") String organizationId,
            @Param("groupId") Long groupId);
}
