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

import dev.axonia.identity.domain.AvatarOverrideUpdate;
import dev.axonia.identity.domain.AvatarSyncUpdate;
import dev.axonia.identity.domain.UserTimeZoneRow;
import dev.axonia.identity.domain.UserUpsert;

public interface UserRepository extends CrudRepository<UserEntity, Long>, UserSearchRepository {

    Optional<UserEntity> findByOrganizationIdAndUserId(String organizationId, String userId);

    List<UserEntity> findByOrganizationIdAndUserIdIn(String organizationId, Collection<String> userIds);

    List<UserEntity> findByOrganizationId(String organizationId, Pageable pageable);

    Optional<UserEntity> findByOrganizationIdAndScimId(String organizationId, String scimId);

    Optional<UserEntity> findByOrganizationIdAndExternalId(String organizationId, String externalId);

    @Query("SELECT " + UserEntity.COLUMNS
            + " FROM identity_user WHERE organization_id = :organizationId AND lower(email) = lower(:email) LIMIT 1")
    Optional<UserEntity> findByOrganizationIdAndEmail(@Param("organizationId") String organizationId,
            @Param("email") String email);

    /**
     * How many people are present in one organization's directory. {@code active = true} rather than {@code IS NOT
     * FALSE} because the column is {@code DEFAULT true NOT NULL}: only SCIM deprovisioning ever writes false. That is
     * the same predicate {@link UserSearchRepositoryImpl} applies for a not-including-departed search and the same one
     * any reporting of active members uses, so the three cannot disagree about who is here.
     */
    @Query("SELECT count(*) FROM identity_user WHERE organization_id = :organizationId AND active = true")
    long countPresentByOrganizationId(@Param("organizationId") String organizationId);

    /**
     * The subjects of everybody present in one organization, for a sender that writes to all of them. Same {@code
     * active = true} predicate as {@link #countPresentByOrganizationId}, so what a recipient list contains and what the
     * console counts can never disagree. {@code user_id IS NOT NULL} because a SCIM-provisioned member who has never
     * signed in has no subject yet: there is nobody to address, and a null in the list would be a recipient with no
     * identity rather than one fewer recipient.
     *
     * <p>
     * Its literal predicate differs from the one {@link dev.axonia.identity.api.UserDirectory#presentOf} derives
     * through {@code UserDirectory.statusOf}, which is effectively {@code active IS NOT FALSE}: the two agree only
     * while the column stays {@code NOT NULL}, and making it nullable would silently split a recipient list from the
     * membership check the delivery gate applies to the same person. That is the one place where getting "present"
     * wrong is a message actually sent, so change this predicate with that one or not at all.
     */
    @Query("""
            SELECT user_id FROM identity_user
            WHERE organization_id = :organizationId AND active = true AND user_id IS NOT NULL
            ORDER BY user_id
            """)
    List<String> findPresentUserIds(@Param("organizationId") String organizationId);

    @Query("SELECT " + UserEntity.COLUMNS + """
             FROM identity_user
            WHERE organization_id = :organizationId
              AND user_id IS NULL
              AND active = true
              AND lower(email) = lower(:email)
            LIMIT 1
            """)
    Optional<UserEntity> findActiveSublessByEmail(@Param("organizationId") String organizationId,
            @Param("email") String email);

    /**
     * Whoever is present at one address, whether or not they have ever signed in - the question
     * {@code SuccessionAdoption} asks of an address a directory push has been rewritten to.
     *
     * <p>
     * Deliberately wider than {@link #findActiveSublessByEmail}: that one reconciles a sign-in onto a row nobody holds
     * yet, so a row already carrying a subject would be the wrong one to attach to. Adoption is the opposite question -
     * it is looking for the person themselves, and a member who signed in long before the migration is exactly who a
     * replacement directory is pushing. {@code active = true} for the reason {@link #countPresentByOrganizationId} uses
     * it: a deprovisioned row is not somebody a new directory is renaming.
     *
     * <p>
     * <strong>The ordering is load-bearing, not tidiness.</strong> Two active rows at one address genuinely can exist:
     * the email index is not unique, SCIM's address conflict is an application-level check only, and
     * {@link dev.axonia.identity.application.UserService}'s reconciliation guard deliberately leaves a second active
     * row standing in a case it names. Adoption is the one operation in this feature that rewrites a member's identity
     * fields, so which of two duplicates becomes the 201 answer must not be whatever plan the planner happens to pick
     * that run. {@code user_id IS NULL} sorts false first, which prefers the row somebody has actually signed in as -
     * the one carrying the subject, the sessions and the history - and {@code id} then breaks the remaining tie towards
     * the older row, which is the one the newer duplicate was a duplicate of. {@link #findActiveSublessByEmail} needs
     * none of this because {@code user_id IS NULL} already narrows it to a set a sign-in keeps to one row.
     */
    @Query("SELECT " + UserEntity.COLUMNS + """
             FROM identity_user
            WHERE organization_id = :organizationId AND active = true AND lower(email) = lower(:email)
            ORDER BY (user_id IS NULL), id
            LIMIT 1
            """)
    Optional<UserEntity> findActiveByEmail(@Param("organizationId") String organizationId,
            @Param("email") String email);

    /**
     * The instant is a plain named parameter, not a SpEL record accessor: Spring Data JDBC's SpEL parameter binding
     * bypasses the {@code Instant} -> timestamp converter, so {@code :#{#user.seenAt}} would reach the driver as an
     * unparseable ISO string. The caller supplies the instant (one {@code Clock} read per sign-in in
     * {@link dev.axonia.identity.application.UserService}) rather than the database computing it, so the write stays
     * free of time-telling logic.
     */
    @Modifying
    @Query("""
            UPDATE identity_user SET user_id = :userId, last_seen_at = :seenAt, updated_at = :seenAt WHERE id = :id
            """)
    void attachSub(@Param("id") Long id, @Param("userId") String userId, @Param("seenAt") Instant seenAt);

    /**
     * Race-safe JIT upsert: two concurrent first requests from the same user must not conflict, so this is a single ON
     * CONFLICT statement (audit columns set in SQL because Spring Data auditing cannot populate a raw-query insert).
     * The instant is a plain named parameter, not a SpEL record accessor, for the same reason as {@link #attachSub}:
     * the caller supplies it rather than the database computing it, so {@code created_at}, {@code updated_at} and
     * {@code last_seen_at} all come from the one instant a single sign-in read from the clock.
     *
     * <p>
     * Two columns are written on insert only and absent from the {@code ON CONFLICT} clause, each for its own reason:
     * {@code scim_id} must stay stable for the lifetime of the row once assigned, and reassigning one on a later
     * sign-in is exactly the identity swap RFC 7643 forbids; {@code arrival} is fixed at {@code 'FIRST_SIGN_IN'} - a
     * SQL literal, not a bound parameter, because this method is only ever the just-in-time path - because how a person
     * arrived is a fact about their first row, not something a later sign-in can rewrite.
     */
    @Modifying
    @Query("""
            INSERT INTO identity_user (organization_id, user_id, display_name, email, scim_id, arrival,
                                       last_seen_at, created_at, updated_at, version)
            VALUES (:#{#user.organizationId}, :#{#user.userId}, :#{#user.displayName}, :#{#user.email},
                    :#{#user.scimId}, 'FIRST_SIGN_IN', :seenAt, :seenAt, :seenAt, 0)
            ON CONFLICT (organization_id, user_id)
            DO UPDATE SET display_name = :#{#user.displayName}, email = :#{#user.email},
                          last_seen_at = :seenAt, updated_at = :seenAt,
                          version = identity_user.version + 1
            """)
    void upsert(@Param("user") UserUpsert user, @Param("seenAt") Instant seenAt);

    /** Writes the outcome of one successful avatar sync. Keyed by (organization, subject) like every other write. */
    default void updateSyncedAvatar(AvatarSyncUpdate update) {
        writeSyncedAvatar(update, update.avatarSyncedAt());
    }

    /**
     * Backing query for {@link #updateSyncedAvatar(AvatarSyncUpdate)}. The timestamp is a plain named parameter, not a
     * SpEL record accessor: Spring Data JDBC's SpEL parameter binding bypasses the {@code Instant} -> timestamp
     * converter, so {@code :#{#u.avatarSyncedAt}} would reach the driver as an unparseable ISO string.
     */
    @Modifying
    @Query("""
            UPDATE identity_user
            SET avatar_media_key = :#{#u.avatarMediaKey}, avatar_etag = :#{#u.avatarEtag},
                avatar_synced_at = :syncedAt, updated_at = :syncedAt
            WHERE organization_id = :#{#u.organizationId} AND user_id = :#{#u.userId}
            """)
    void writeSyncedAvatar(@Param("u") AvatarSyncUpdate u, @Param("syncedAt") Instant syncedAt);

    /** Stamps a successful "checked, unchanged" sync without touching the avatar itself. */
    @Modifying
    @Query("""
            UPDATE identity_user SET avatar_synced_at = :syncedAt, updated_at = :syncedAt
            WHERE organization_id = :organizationId AND user_id = :userId
            """)
    void touchAvatarSyncedAt(@Param("organizationId") String organizationId, @Param("userId") String userId,
            @Param("syncedAt") Instant syncedAt);

    /** The user no longer has a photo in the directory: drop the synced avatar, remember the check. */
    @Modifying
    @Query("""
            UPDATE identity_user
            SET avatar_media_key = NULL, avatar_etag = NULL,
                avatar_synced_at = :syncedAt, updated_at = :syncedAt
            WHERE organization_id = :organizationId AND user_id = :userId
            """)
    void clearSyncedAvatar(@Param("organizationId") String organizationId, @Param("userId") String userId,
            @Param("syncedAt") Instant syncedAt);

    /**
     * Points a member's profile at their own uploaded picture. The three identifying/target values travel through
     * {@link AvatarOverrideUpdate} (a {@code @Builder} record) rather than as separate parameters, to stay under PMD's
     * {@code ExcessiveParameterList} threshold of 5. The timestamp is a plain named parameter, not a SpEL record
     * accessor: Spring Data JDBC's SpEL parameter binding bypasses the {@code Instant} -> timestamp converter, so
     * {@code :#{#o.updatedAt}} would reach the driver as an unparseable ISO string - the same reason {@link #attachSub}
     * and {@link #writeSyncedAvatar} keep their timestamps outside the record they otherwise bind through SpEL.
     */
    @Modifying
    @Query("""
            UPDATE identity_user
            SET avatar_override_key = :#{#o.key}, updated_at = :updatedAt, version = version + 1
            WHERE organization_id = :#{#o.organizationId} AND user_id = :#{#o.userId}
            """)
    void updateAvatarOverride(@Param("o") AvatarOverrideUpdate o, @Param("updatedAt") Instant updatedAt);

    /**
     * The effective clock of each named member, omitting anyone who has none.
     *
     * <p>
     * Partial on purpose. A result carrying an entry for every id could not distinguish a reading from a default, and a
     * default is the one answer this must never give: it would report a whole community sitting in a zone nobody
     * measured. Callers short-circuit an empty collection before reaching here, because {@code IN ()} is not valid SQL.
     */
    @Query("""
            SELECT user_id, COALESCE(declared_time_zone, observed_time_zone) AS time_zone
            FROM identity_user
            WHERE organization_id = :organizationId AND user_id IN (:userIds)
              AND COALESCE(declared_time_zone, observed_time_zone) IS NOT NULL
            """)
    List<UserTimeZoneRow> findTimeZones(@Param("organizationId") String organizationId,
            @Param("userIds") Collection<String> userIds);

    /**
     * Records what a member's browser reported. {@code IS DISTINCT FROM} makes a repeat report a no-op rather than a
     * version bump: the frontend sends only on drift, and this holds the guarantee regardless of what any caller does.
     * The timestamp is a plain named parameter for the reason {@link #attachSub} gives.
     */
    @Modifying
    @Query("""
            UPDATE identity_user
            SET observed_time_zone = :zone, updated_at = :updatedAt, version = version + 1
            WHERE organization_id = :organizationId AND user_id = :userId
              AND observed_time_zone IS DISTINCT FROM :zone
            """)
    void updateObservedTimeZone(@Param("organizationId") String organizationId, @Param("userId") String userId,
            @Param("zone") String zone, @Param("updatedAt") Instant updatedAt);

    /** The member's own answer, which wins over the observed reading without stopping it. */
    @Modifying
    @Query("""
            UPDATE identity_user
            SET declared_time_zone = :zone, updated_at = :updatedAt, version = version + 1
            WHERE organization_id = :organizationId AND user_id = :userId
            """)
    void updateDeclaredTimeZone(@Param("organizationId") String organizationId, @Param("userId") String userId,
            @Param("zone") String zone, @Param("updatedAt") Instant updatedAt);

    /** Drops the declaration; the observed reading underneath becomes the effective clock again. */
    @Modifying
    @Query("""
            UPDATE identity_user
            SET declared_time_zone = NULL, updated_at = :updatedAt, version = version + 1
            WHERE organization_id = :organizationId AND user_id = :userId
            """)
    void clearDeclaredTimeZone(@Param("organizationId") String organizationId, @Param("userId") String userId,
            @Param("updatedAt") Instant updatedAt);

    /** Drops the override; the mirrored avatar underneath becomes what is served again. */
    @Modifying
    @Query("""
            UPDATE identity_user
            SET avatar_override_key = NULL, updated_at = :updatedAt, version = version + 1
            WHERE organization_id = :organizationId AND user_id = :userId
            """)
    void clearAvatarOverride(@Param("organizationId") String organizationId, @Param("userId") String userId,
            @Param("updatedAt") Instant updatedAt);
}
