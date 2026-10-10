package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.identity.domain.UserArrival;
import dev.axonia.identity.domain.UserUpsert;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryScimTest extends CoreJdbcTestSupport {

    private static final Instant SEEN_AT = Instant.parse("2026-07-25T12:00:00Z");

    @Autowired
    UserRepository repository;

    @Test
    void findsSublessRecordByEmail_thenAttachesSub() {

        // given
        var saved = repository.save(
                UserEntity.builder().organizationId("default").userId(null).scimId("scim-1").arrival(UserArrival.SYNCED)
                        .externalId("okta-1").displayName("Alice").email("Alice@Acme.test").active(true).build());

        var candidate = repository.findActiveSublessByEmail("default", "alice@acme.test");
        assertThat(candidate).isPresent();

        // when
        repository.attachSub(saved.getId(), "sub-123", SEEN_AT);

        // then
        var attached = repository.findByOrganizationIdAndUserId("default", "sub-123");
        assertThat(attached).isPresent();
        assertThat(repository.findActiveSublessByEmail("default", "alice@acme.test")).isEmpty();
    }

    @Test
    void deProvisionedSublessRecord_isExcludedFromLoginReconciliation() {

        // given
        repository.save(UserEntity.builder().organizationId("default").userId(null).scimId("scim-deprovisioned")
                .arrival(UserArrival.SYNCED).externalId("okta-deprovisioned").displayName("Carol")
                .email("carol@acme.test").active(false).build());

        // then
        assertThat(repository.findActiveSublessByEmail("default", "carol@acme.test")).isEmpty();
    }

    @Test
    void attachSubDoesNotTouchTheAvatar() {

        // given — a SCIM-provisioned record that already carries a Graph-synced avatar
        var saved = repository.save(UserEntity.builder().organizationId("default").userId(null).scimId("scim-3")
                .arrival(UserArrival.SYNCED).externalId("entra-3").displayName("Dana").email("dana@acme.test")
                .active(true).avatarMediaKey("avatars/default/x.jpg").build());

        // when - first login
        repository.attachSub(saved.getId(), "sub-dana", SEEN_AT);

        // then
        var attached = repository.findByOrganizationIdAndUserId("default", "sub-dana");
        assertThat(attached.orElseThrow().getAvatarMediaKey()).isEqualTo("avatars/default/x.jpg");
    }

    @Test
    void findsByScimAndExternalId() {

        // given
        repository.save(UserEntity.builder().organizationId("default").scimId("scim-2").arrival(UserArrival.SYNCED)
                .externalId("okta-2").displayName("Bob").email("bob@acme.test").active(true).build());

        // then
        assertThat(repository.findByOrganizationIdAndScimId("default", "scim-2")).isPresent();
        assertThat(repository.findByOrganizationIdAndExternalId("default", "okta-2")).isPresent();
    }

    @Test
    void aScimCreatedRowLandsSynced() {

        // given - the shape ScimUserService.create actually saves: stamped SYNCED explicitly, never left to a default
        repository.save(UserEntity.builder().organizationId("default").scimId("scim-synced-check")
                .arrival(UserArrival.SYNCED).externalId("okta-synced-check").displayName("Fay").email("fay@acme.test")
                .active(true).build());

        // when - read back a fresh row from the database, not the in-memory object just built
        var stored = repository.findByOrganizationIdAndScimId("default", "scim-synced-check").orElseThrow();

        // then
        assertThat(stored.getArrival()).isEqualTo(UserArrival.SYNCED);
    }

    @Test
    void upsertStampsLastSeenAt() {

        // when
        repository.upsert(UserUpsert.builder().organizationId("default").userId("u-new").displayName("Ada Lovelace")
                .email("ada@acme.test").scimId("scim-u-new").build(), SEEN_AT);

        // then
        var stored = repository.findByOrganizationIdAndUserId("default", "u-new").orElseThrow();
        assertThat(stored.getLastSeenAt()).isEqualTo(SEEN_AT);
        assertThat(stored.getArrival()).isEqualTo(UserArrival.FIRST_SIGN_IN);
    }

    @Test
    void repeatedUpsertMovesLastSeenForward() {

        // given
        var user = UserUpsert.builder().organizationId("default").userId("u-new").displayName("Ada Lovelace")
                .email("ada@acme.test").scimId("scim-u-new").build();
        var first = Instant.parse("2026-07-25T12:00:00Z");
        var second = Instant.parse("2026-07-25T12:05:00Z");
        repository.upsert(user, first);

        // when
        repository.upsert(user, second);

        // then
        var stored = repository.findByOrganizationIdAndUserId("default", "u-new").orElseThrow();
        assertThat(stored.getLastSeenAt()).isEqualTo(second);
    }

    @Test
    void attachSubStampsLastSeenAt() {

        // given a SCIM row that has never signed in
        var saved = repository.save(
                UserEntity.builder().organizationId("default").userId(null).scimId("scim-4").arrival(UserArrival.SYNCED)
                        .externalId("okta-4").displayName("Erin").email("erin@acme.test").active(true).build());
        assertThat(repository.findById(saved.getId()).orElseThrow().getLastSeenAt()).isNull();

        // when she signs in for the first time
        repository.attachSub(saved.getId(), "sub-erin", SEEN_AT);

        // then the same row is reconciled and stamped, not duplicated
        var attached = repository.findByOrganizationIdAndUserId("default", "sub-erin").orElseThrow();
        assertThat(attached.getId()).isEqualTo(saved.getId());
        assertThat(attached.getLastSeenAt()).isEqualTo(SEEN_AT);
    }
}
