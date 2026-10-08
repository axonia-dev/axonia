package dev.axonia.identity.internal;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import dev.axonia.Entity;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
@Table("identity_user")
public class UserEntity implements Entity {

    /**
     * The select list of every hand-written query that returns a member. Never {@code SELECT *}: a statement prepared
     * over every column fails on the release still serving once a migration drops one.
     */
    static final String COLUMNS = "id, organization_id, user_id, display_name, email, active, external_id, scim_id,"
            + " arrival, avatar_media_key, avatar_etag, avatar_synced_at, avatar_override_key, last_seen_at,"
            + " observed_time_zone, declared_time_zone, business_unit, department, team, created_at, updated_at,"
            + " version";

    @Id
    private Long id;

    private String organizationId;
    private String userId;
    private String displayName;
    private String email;
    private Boolean active;
    private String externalId;
    private String scimId;

    /**
     * Deliberately no {@code @Builder.Default}: the column has no default either (see V41), so a caller that forgets to
     * stamp this fails loudly with a NOT NULL violation at insert instead of silently landing {@code SYNCED}.
     */
    private UserArrival arrival;

    private String avatarMediaKey;
    private String avatarEtag;
    private Instant avatarSyncedAt;

    /**
     * The member's own picture, which wins over the mirrored one wherever an avatar is resolved. Held in its own
     * columns so {@link AvatarSyncService} can keep refreshing the mirror underneath without either pipeline reading or
     * collecting the other's blob.
     */
    private String avatarOverrideKey;

    /**
     * When this person was last actually seen making an authenticated request. Distinct from {@code updatedAt}, which
     * SCIM and avatar syncs also move, and which therefore cannot answer "has this person ever come back". Null means
     * not recorded rather than never active: rows predating the column keep it until their owner next signs in.
     */
    private Instant lastSeenAt;

    /**
     * Where this member sits in time. {@code declaredTimeZone} is their own answer and wins wherever both are set;
     * {@code observedTimeZone} keeps refreshing underneath it, so clearing a declaration lands on a current reading
     * rather than a stale one - the same arrangement {@code avatarOverrideKey} has with {@code avatarMediaKey}. Both
     * null means unknown, which is a state every reader must be able to tell from a measurement.
     */
    private String observedTimeZone;

    private String declaredTimeZone;

    private String businessUnit;
    private String department;
    private String team;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}
