package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.identity.domain.AvatarOverrideUpdate;
import dev.axonia.identity.domain.AvatarSyncUpdate;
import dev.axonia.identity.domain.UserArrival;
import dev.axonia.identity.domain.UserTimeZoneRow;
import dev.axonia.identity.domain.UserUpsert;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryTest extends CoreJdbcTestSupport {

    private static final Instant SEEN_AT = Instant.parse("2026-07-25T12:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Test
    void upsert() {

        // given
        var entity = UserEntityMother.any().build();

        // when
        userRepository.upsert(upsertOf(entity), SEEN_AT);

        // then
        var actual = userRepository.findByOrganizationIdAndUserId(entity.getOrganizationId(), entity.getUserId());
        assertThat(actual).isPresent();
        assertThat(actual.get().getDisplayName()).isEqualTo("Alice Anderson");
        assertThat(actual.get().getEmail()).isEqualTo("alice@axonia.example");
        // the upsert never touches the mirrored avatar: see AvatarSyncService
        assertThat(actual.get().getAvatarMediaKey()).isNull();
        assertThat(actual.get().getCreatedAt()).isNotNull();
        // The insert branch leaves the row at its first version, which is how UserService tells a sign-up from a
        // returning member; upsert_isIdempotentAndRefreshes pins the other half.
        assertThat(actual.get().getVersion()).isZero();
    }

    @Test
    void upsert_isIdempotentAndRefreshes() {

        // given
        var entity = UserEntityMother.any().build();
        userRepository.upsert(upsertOf(entity), SEEN_AT);

        // when
        userRepository.upsert(UserUpsert.builder().organizationId(entity.getOrganizationId()).userId(entity.getUserId())
                .displayName("Alice A.").email("alice@new.dev").scimId("scim-repo-refresh").build(), SEEN_AT);

        // then
        var actual = userRepository.findByOrganizationIdAndUserIdIn(entity.getOrganizationId(),
                List.of(entity.getUserId()));
        assertThat(actual).hasSize(1);
        assertThat(actual.getFirst().getDisplayName()).isEqualTo("Alice A.");
        assertThat(actual.getFirst().getEmail()).isEqualTo("alice@new.dev");
        assertThat(actual.getFirst().getVersion()).isEqualTo(1L);
        // scim_id and arrival are insert-only: the second upsert's different scimId ("scim-repo-refresh")
        // must never overwrite the first, and arrival stays FIRST_SIGN_IN, stamped once by the JIT path
        assertThat(actual.getFirst().getScimId()).isEqualTo(entity.getScimId());
        assertThat(actual.getFirst().getArrival()).isEqualTo(UserArrival.FIRST_SIGN_IN);
    }

    @Test
    void findByOrganizationIdAndUserIdIn_isTenantScoped() {

        // given
        var defaultTenantUser = UserEntityMother.any().build();
        userRepository.upsert(upsertOf(defaultTenantUser), SEEN_AT);
        userRepository.upsert(UserUpsert.builder().organizationId("acme").userId("44444444-4444-4444-4444-444444444444")
                .displayName("Dave Diaz").email("dave@acme.dev").scimId("44444444-4444-4444-4444-444444444444").build(),
                SEEN_AT);

        // when
        var actual = userRepository.findByOrganizationIdAndUserIdIn("acme",
                List.of(defaultTenantUser.getUserId(), "44444444-4444-4444-4444-444444444444"));

        // then
        assertThat(actual).hasSize(1);
        assertThat(actual.getFirst().getUserId()).isEqualTo("44444444-4444-4444-4444-444444444444");
    }

    @Test
    void findByOrganizationIdAndUserId_missing() {

        // when
        var actual = userRepository.findByOrganizationIdAndUserId("default", "no-such-sub");

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void updateSyncedAvatar_writesAllSyncColumns() {

        // given
        var entity = UserEntityMother.any().build();
        userRepository.upsert(upsertOf(entity), SEEN_AT);
        var syncedAt = Instant.parse("2026-07-07T10:00:00Z");

        // when
        userRepository.updateSyncedAvatar(AvatarSyncUpdate.builder().organizationId(entity.getOrganizationId())
                .userId(entity.getUserId()).avatarMediaKey("avatars/default/a.jpg").avatarEtag("W/\"etag-1\"")
                .avatarSyncedAt(syncedAt).build());

        // then
        var actual = userRepository.findByOrganizationIdAndUserId(entity.getOrganizationId(), entity.getUserId())
                .orElseThrow();
        assertThat(actual.getAvatarMediaKey()).isEqualTo("avatars/default/a.jpg");
        assertThat(actual.getAvatarEtag()).isEqualTo("W/\"etag-1\"");
        assertThat(actual.getAvatarSyncedAt()).isEqualTo(syncedAt);
    }

    @Test
    void touchAvatarSyncedAt_stampsOnlyTheTimestamp() {

        // given - a member whose avatar is already mirrored (the etag-unchanged and hash-match "no-op sync"
        // paths in AvatarSyncService only call touchAvatarSyncedAt, and rely on it leaving the mirror alone)
        var entity = UserEntityMother.any().build();
        userRepository.upsert(upsertOf(entity), SEEN_AT);
        userRepository.updateSyncedAvatar(AvatarSyncUpdate.builder().organizationId(entity.getOrganizationId())
                .userId(entity.getUserId()).avatarMediaKey("avatars/default/a.jpg").avatarEtag("W/\"etag-1\"")
                .avatarSyncedAt(Instant.parse("2026-07-06T10:00:00Z")).build());
        var syncedAt = Instant.parse("2026-07-07T10:00:00Z");

        // when
        userRepository.touchAvatarSyncedAt(entity.getOrganizationId(), entity.getUserId(), syncedAt);

        // then - only the timestamp moved; the mirrored avatar survives untouched
        var actual = userRepository.findByOrganizationIdAndUserId(entity.getOrganizationId(), entity.getUserId())
                .orElseThrow();
        assertThat(actual.getAvatarSyncedAt()).isEqualTo(syncedAt);
        assertThat(actual.getAvatarMediaKey()).isEqualTo("avatars/default/a.jpg");
        assertThat(actual.getAvatarEtag()).isEqualTo("W/\"etag-1\"");
    }

    @Test
    void clearSyncedAvatar_removesAvatarAndStamps() {

        // given - a previously synced avatar
        var entity = UserEntityMother.any().build();
        userRepository.upsert(upsertOf(entity), SEEN_AT);
        userRepository.updateSyncedAvatar(AvatarSyncUpdate.builder().organizationId(entity.getOrganizationId())
                .userId(entity.getUserId()).avatarMediaKey("avatars/default/a.jpg").avatarEtag("W/\"etag-1\"")
                .avatarSyncedAt(Instant.parse("2026-07-06T10:00:00Z")).build());
        var clearedAt = Instant.parse("2026-07-07T10:00:00Z");

        // when
        userRepository.clearSyncedAvatar(entity.getOrganizationId(), entity.getUserId(), clearedAt);

        // then
        var actual = userRepository.findByOrganizationIdAndUserId(entity.getOrganizationId(), entity.getUserId())
                .orElseThrow();
        assertThat(actual.getAvatarMediaKey()).isNull();
        assertThat(actual.getAvatarEtag()).isNull();
        assertThat(actual.getAvatarSyncedAt()).isEqualTo(clearedAt);
    }

    @Test
    void doesNotOverwriteAMirroredAvatarOnASubsequentUpsert() {

        var organizationId = "default";
        var userId = "55555555-5555-5555-5555-555555555555";

        userRepository.upsert(UserUpsert.builder().organizationId(organizationId).userId(userId).displayName("Ada")
                .email("ada@example.com").scimId(userId).build(), SEEN_AT);
        userRepository.updateSyncedAvatar(AvatarSyncUpdate.builder().organizationId(organizationId).userId(userId)
                .avatarMediaKey("avatars/default/a.png").avatarEtag("hash").avatarSyncedAt(SEEN_AT).build());

        userRepository.upsert(UserUpsert.builder().organizationId(organizationId).userId(userId).displayName("Ada")
                .email("ada@example.com").scimId(userId).build(), SEEN_AT.plusSeconds(60));

        var user = userRepository.findByOrganizationIdAndUserId(organizationId, userId).orElseThrow();
        assertThat(user.getAvatarMediaKey()).isEqualTo("avatars/default/a.png");
    }

    @Test
    void countPresentByOrganizationId_countsOnlyPresentPeopleOfThatTenant() {

        // given - two in "default" (one since deprovisioned), one in another tenant
        userRepository.upsert(upsertOf(UserEntityMother.any().build()), SEEN_AT);
        userRepository.upsert(UserUpsert.builder().organizationId("default")
                .userId("22222222-2222-2222-2222-222222222222").displayName("Bob Brandt").email("bob@axonia.example")
                .scimId("22222222-2222-2222-2222-222222222222").build(), SEEN_AT);
        userRepository.upsert(UserUpsert.builder().organizationId("acme").userId("44444444-4444-4444-4444-444444444444")
                .displayName("Dave Diaz").email("dave@acme.dev").scimId("44444444-4444-4444-4444-444444444444").build(),
                SEEN_AT);
        var bob = userRepository.findByOrganizationIdAndUserId("default", "22222222-2222-2222-2222-222222222222")
                .orElseThrow();
        userRepository.save(bob.toBuilder().active(false).build());

        // when
        var actual = userRepository.countPresentByOrganizationId("default");

        // then - the deactivated member and the other tenant's member are both out
        assertThat(actual).isEqualTo(1L);
    }

    @Test
    void countPresentByOrganizationId_isZeroForATenantWithNobody() {

        // when
        var actual = userRepository.countPresentByOrganizationId("empty-tenant");

        // then
        assertThat(actual).isZero();
    }

    @Test
    void updateAvatarOverride_pointsTheProfileAtItAndBumpsVersion() {

        // given
        var entity = UserEntityMother.any().build();
        userRepository.upsert(upsertOf(entity), SEEN_AT);
        var seeded = userRepository.findByOrganizationIdAndUserId(entity.getOrganizationId(), entity.getUserId())
                .orElseThrow();
        var updatedAt = Instant.parse("2026-07-08T10:00:00Z");

        // when
        userRepository.updateAvatarOverride(AvatarOverrideUpdate.builder().organizationId(entity.getOrganizationId())
                .userId(entity.getUserId()).key("avatars/default/own.png").build(), updatedAt);

        // then
        var actual = userRepository.findByOrganizationIdAndUserId(entity.getOrganizationId(), entity.getUserId())
                .orElseThrow();
        assertThat(actual.getAvatarOverrideKey()).isEqualTo("avatars/default/own.png");
        assertThat(actual.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(actual.getVersion()).isEqualTo(seeded.getVersion() + 1);
    }

    @Test
    void clearAvatarOverride_dropsTheColumnsAndBumpsVersion() {

        // given - a member who already has an override in place
        var entity = UserEntityMother.any().build();
        userRepository.upsert(upsertOf(entity), SEEN_AT);
        userRepository
                .updateAvatarOverride(
                        AvatarOverrideUpdate.builder().organizationId(entity.getOrganizationId())
                                .userId(entity.getUserId()).key("avatars/default/own.png").build(),
                        Instant.parse("2026-07-08T10:00:00Z"));
        var seeded = userRepository.findByOrganizationIdAndUserId(entity.getOrganizationId(), entity.getUserId())
                .orElseThrow();
        var clearedAt = Instant.parse("2026-07-09T10:00:00Z");

        // when
        userRepository.clearAvatarOverride(entity.getOrganizationId(), entity.getUserId(), clearedAt);

        // then
        var actual = userRepository.findByOrganizationIdAndUserId(entity.getOrganizationId(), entity.getUserId())
                .orElseThrow();
        assertThat(actual.getAvatarOverrideKey()).isNull();
        assertThat(actual.getUpdatedAt()).isEqualTo(clearedAt);
        assertThat(actual.getVersion()).isEqualTo(seeded.getVersion() + 1);
    }

    @Test
    void findTimeZones_prefersTheDeclarationOverTheObservation() {

        // given - a member whose browser says Brussels and who has said New York themselves
        var entity = UserEntityMother.any().build();
        userRepository.upsert(upsertOf(entity), SEEN_AT);
        userRepository.updateObservedTimeZone(entity.getOrganizationId(), entity.getUserId(), "Europe/Brussels",
                SEEN_AT);
        userRepository.updateDeclaredTimeZone(entity.getOrganizationId(), entity.getUserId(), "America/New_York",
                SEEN_AT);

        // when
        var actual = userRepository.findTimeZones(entity.getOrganizationId(), List.of(entity.getUserId()));

        // then
        assertThat(actual).containsExactly(new UserTimeZoneRow(entity.getUserId(), "America/New_York"));
    }

    @Test
    void clearDeclaredTimeZone_fallsBackToWhatTheBrowserReported() {

        // given
        var entity = UserEntityMother.any().build();
        userRepository.upsert(upsertOf(entity), SEEN_AT);
        userRepository.updateObservedTimeZone(entity.getOrganizationId(), entity.getUserId(), "Europe/Brussels",
                SEEN_AT);
        userRepository.updateDeclaredTimeZone(entity.getOrganizationId(), entity.getUserId(), "America/New_York",
                SEEN_AT);

        // when
        userRepository.clearDeclaredTimeZone(entity.getOrganizationId(), entity.getUserId(), SEEN_AT);

        // then - the observation kept refreshing underneath, so dropping the declaration lands on it
        assertThat(userRepository.findTimeZones(entity.getOrganizationId(), List.of(entity.getUserId())))
                .containsExactly(new UserTimeZoneRow(entity.getUserId(), "Europe/Brussels"));
    }

    /**
     * The one answer this query must never give is a default. A member nobody has measured is absent from the result,
     * so a caller can tell "no clock" from "this clock" - which a row reading UTC could not.
     */
    @Test
    void findTimeZones_omitsAMemberWithNoClock() {

        // given
        var entity = UserEntityMother.any().build();
        userRepository.upsert(upsertOf(entity), SEEN_AT);

        // when
        var actual = userRepository.findTimeZones(entity.getOrganizationId(), List.of(entity.getUserId()));

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void updateObservedTimeZone_isANoOpWhenTheReadingHasNotMoved() {

        // given
        var entity = UserEntityMother.any().build();
        userRepository.upsert(upsertOf(entity), SEEN_AT);
        userRepository.updateObservedTimeZone(entity.getOrganizationId(), entity.getUserId(), "Europe/Brussels",
                SEEN_AT);
        var seeded = userRepository.findByOrganizationIdAndUserId(entity.getOrganizationId(), entity.getUserId())
                .orElseThrow();

        // when - the same browser reports the same zone again
        userRepository.updateObservedTimeZone(entity.getOrganizationId(), entity.getUserId(), "Europe/Brussels",
                Instant.parse("2026-07-26T12:00:00Z"));

        // then - no version bump and no new timestamp: the row did not change
        var actual = userRepository.findByOrganizationIdAndUserId(entity.getOrganizationId(), entity.getUserId())
                .orElseThrow();
        assertThat(actual.getVersion()).isEqualTo(seeded.getVersion());
        assertThat(actual.getUpdatedAt()).isEqualTo(seeded.getUpdatedAt());
    }

    @Test
    void findTimeZones_isTenantScoped() {

        // given - the same subject present in two organizations, each with its own clock
        var entity = UserEntityMother.any().build();
        userRepository.upsert(upsertOf(entity), SEEN_AT);
        userRepository.upsert(UserUpsert.builder().organizationId("acme").userId(entity.getUserId())
                .displayName("Alice Anderson").email("alice@acme.dev").scimId("scim-acme").build(), SEEN_AT);
        userRepository.updateObservedTimeZone("default", entity.getUserId(), "Europe/Brussels", SEEN_AT);
        userRepository.updateObservedTimeZone("acme", entity.getUserId(), "Asia/Kolkata", SEEN_AT);

        // when
        var actual = userRepository.findTimeZones("acme", List.of(entity.getUserId()));

        // then
        assertThat(actual).containsExactly(new UserTimeZoneRow(entity.getUserId(), "Asia/Kolkata"));
    }

    private static UserUpsert upsertOf(UserEntity entity) {

        return UserUpsert.builder().organizationId(entity.getOrganizationId()).userId(entity.getUserId())
                .displayName(entity.getDisplayName()).email(entity.getEmail()).scimId(entity.getScimId()).build();
    }
}
