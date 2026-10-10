package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface DirectoryGroupRepository extends CrudRepository<DirectoryGroupEntity, Long> {

    Optional<DirectoryGroupEntity> findByOrganizationIdAndId(String organizationId, Long id);

    Optional<DirectoryGroupEntity> findByOrganizationIdAndScimId(String organizationId, String scimId);

    Optional<DirectoryGroupEntity> findByOrganizationIdAndReconciliationKey(String organizationId,
            String reconciliationKey);

    List<DirectoryGroupEntity> findByOrganizationIdAndDisplayName(String organizationId, String displayName);

    List<DirectoryGroupEntity> findByOrganizationIdAndReconciliationKeyIn(String organizationId,
            Collection<String> reconciliationKeys);

    List<DirectoryGroupEntity> findByOrganizationId(String organizationId, Pageable pageable);

    List<DirectoryGroupEntity> findByOrganizationIdAndConfirmedAtIsNull(String organizationId);

    /**
     * Every synced group a user belongs to, in one indexed join over {@code (organization_id, group_id)}. Resolving the
     * memberships and then loading each group by id costs one query per membership, which the authorization path pays
     * on every permission check of an affected caller.
     */
    @Query("""
            SELECT g.* FROM directory_group g
            JOIN directory_group_member m ON m.organization_id = g.organization_id AND m.group_id = g.id
            WHERE g.organization_id = :organizationId AND m.user_scim_id = :userScimId
            """)
    List<DirectoryGroupEntity> findByOrganizationIdAndMemberScimId(@Param("organizationId") String organizationId,
            @Param("userScimId") String userScimId);

    /**
     * Rewrites every group's reconciliation key to the Entra convention, the external object id.
     *
     * <p>
     * Rows whose {@code external_id} is null are deliberately left untouched. The column is {@code NOT NULL}, and even
     * if it were not, a null key is unmatchable by the authorization gate, so nulling it would silently deny every role
     * bound to that group - the exact failure this rewrite exists to remove. {@link #countGroupsWithoutExternalId} lets
     * the caller report the gap instead of hiding it.
     *
     * <p>
     * {@code version} is bumped so that a concurrently loaded entity fails optimistic locking rather than quietly
     * writing the stale key back over the corrected one.
     *
     * <p>
     * {@code confirmed_at} is deliberately NOT touched here. Only a group landing on a key some role binding is already
     * waiting on has to be held again, and whether a key is claimed is a fact about {@code role_binding}, which lives
     * in another module - {@link dev.axonia.identity.application.DirectoryGroupRekeyer} asks
     * {@link dev.axonia.identity.api.spi.DirectoryGroupClaims} and holds exactly those rows through {@link #holdByIds}
     * before this statement runs. See {@link #findConfirmedGroupsRekeyingToExternalId}.
     */
    @Modifying
    @Query("""
            UPDATE directory_group
            SET reconciliation_key = external_id, updated_at = now(), version = version + 1
            WHERE organization_id = :organizationId
              AND external_id IS NOT NULL
              AND reconciliation_key IS DISTINCT FROM external_id
            """)
    int rekeyToExternalId(@Param("organizationId") String organizationId);

    /**
     * The confirmed groups {@link #rekeyToExternalId} is about to move, so the rekeyer can ask which of them land on a
     * claimed key. The {@code WHERE} clause is character-for-character the one the {@code UPDATE} uses, plus
     * {@code confirmed_at IS NOT NULL}, so the two cannot drift into disagreeing about which rows move.
     */
    @Query("""
            SELECT * FROM directory_group
            WHERE organization_id = :organizationId
              AND external_id IS NOT NULL
              AND reconciliation_key IS DISTINCT FROM external_id
              AND confirmed_at IS NOT NULL
            """)
    List<DirectoryGroupEntity> findConfirmedGroupsRekeyingToExternalId(@Param("organizationId") String organizationId);

    /**
     * Rewrites every group's reconciliation key to the non-Entra convention, the display name.
     *
     * <p>
     * The {@code display_name IS NOT NULL} guard mirrors {@link #rekeyToExternalId}'s. {@code display_name} is
     * {@code NOT NULL} today, so it never excludes a row; it is here so that relaxing that column can never turn this
     * rewrite into the silent denial the Entra branch is careful to avoid. {@code confirmed_at} is left alone for the
     * reason {@link #rekeyToExternalId} gives, and the mirror finder below serves the same purpose.
     */
    @Modifying
    @Query("""
            UPDATE directory_group
            SET reconciliation_key = display_name, updated_at = now(), version = version + 1
            WHERE organization_id = :organizationId
              AND display_name IS NOT NULL
              AND reconciliation_key IS DISTINCT FROM display_name
            """)
    int rekeyToDisplayName(@Param("organizationId") String organizationId);

    /** The mirror of {@link #findConfirmedGroupsRekeyingToExternalId} for the display-name convention. */
    @Query("""
            SELECT * FROM directory_group
            WHERE organization_id = :organizationId
              AND display_name IS NOT NULL
              AND reconciliation_key IS DISTINCT FROM display_name
              AND confirmed_at IS NOT NULL
            """)
    List<DirectoryGroupEntity> findConfirmedGroupsRekeyingToDisplayName(@Param("organizationId") String organizationId);

    /**
     * Takes the confirmation back off the named groups, so they confer nothing until an administrator vouches for them
     * under their new key.
     *
     * <p>
     * {@code updated_at} is bound from the caller's {@link java.time.Clock} rather than stamped with SQL {@code now()}:
     * the application drives time. The two rekey statements above still call {@code now()}, which is debt not widened
     * here.
     */
    @Modifying
    @Query("""
            UPDATE directory_group
            SET confirmed_at = NULL, updated_at = :updatedAt, version = version + 1
            WHERE organization_id = :organizationId
              AND id IN (:ids)
              AND confirmed_at IS NOT NULL
            """)
    int holdByIds(@Param("organizationId") String organizationId, @Param("ids") Collection<Long> ids,
            @Param("updatedAt") Instant updatedAt);

    @Query("SELECT count(*) FROM directory_group WHERE organization_id = :organizationId AND external_id IS NULL")
    int countGroupsWithoutExternalId(@Param("organizationId") String organizationId);
}
