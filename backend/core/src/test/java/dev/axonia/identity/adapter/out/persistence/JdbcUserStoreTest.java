package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import dev.axonia.identity.domain.AvatarOverrideUpdate;
import dev.axonia.identity.domain.AvatarSyncUpdate;
import dev.axonia.identity.domain.UserAccount;
import dev.axonia.identity.domain.UserArrival;
import dev.axonia.identity.domain.UserSearch;
import dev.axonia.identity.domain.UserTimeZoneRow;
import dev.axonia.identity.domain.UserUpsert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcUserStoreTest {

    private static final UserEntity ENTITY = UserEntity.builder().id(10L).organizationId("organizationId-1")
            .userId("userId-2").displayName("displayName-3").email("email-4").active(Boolean.TRUE)
            .externalId("externalId-6").scimId("scimId-7").arrival(UserArrival.SYNCED)
            .avatarMediaKey("avatarMediaKey-9").avatarEtag("avatarEtag-10")
            .avatarSyncedAt(Instant.parse("2026-03-03T10:00:00Z")).avatarOverrideKey("avatarOverrideKey-12")
            .lastSeenAt(Instant.parse("2026-05-05T10:00:00Z")).observedTimeZone("observedTimeZone-14")
            .declaredTimeZone("declaredTimeZone-15").businessUnit("businessUnit-16").department("department-17")
            .team("team-18").createdAt(Instant.parse("2026-02-02T10:00:00Z"))
            .updatedAt(Instant.parse("2026-03-03T10:00:00Z")).version(31L).build();

    private static final UserAccount DOMAIN = JdbcUserStore.toUserAccount(ENTITY);

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JdbcUserStore store;

    @Test
    void mapsEveryColumnBothWays() {

        assertThat(DOMAIN).usingRecursiveComparison().isEqualTo(ENTITY);
        assertThat(JdbcUserStore.toEntity(DOMAIN)).usingRecursiveComparison().isEqualTo(ENTITY);
    }

    @Test
    void findByOrganizationIdAndUserIdMapsTheRowItFinds() {

        when(userRepository.findByOrganizationIdAndUserId("organizationId-0", "userId-1"))
                .thenReturn(Optional.of(ENTITY));

        assertThat(store.findByOrganizationIdAndUserId("organizationId-0", "userId-1")).contains(DOMAIN);
    }

    @Test
    void findByOrganizationIdAndUserIdInMapsEveryRowItFinds() {

        when(userRepository.findByOrganizationIdAndUserIdIn("organizationId-0", List.of("a", "b")))
                .thenReturn(List.of(ENTITY));

        assertThat(store.findByOrganizationIdAndUserIdIn("organizationId-0", List.of("a", "b")))
                .containsExactly(DOMAIN);
    }

    @Test
    void findByOrganizationIdMapsEveryRowItFinds() {

        when(userRepository.findByOrganizationId("organizationId-0", PageRequest.of(0, 10)))
                .thenReturn(List.of(ENTITY));

        assertThat(store.findByOrganizationId("organizationId-0", 10)).containsExactly(DOMAIN);
    }

    @Test
    void findByOrganizationIdAndScimIdMapsTheRowItFinds() {

        when(userRepository.findByOrganizationIdAndScimId("organizationId-0", "scimId-1"))
                .thenReturn(Optional.of(ENTITY));

        assertThat(store.findByOrganizationIdAndScimId("organizationId-0", "scimId-1")).contains(DOMAIN);
    }

    @Test
    void findByOrganizationIdAndExternalIdMapsTheRowItFinds() {

        when(userRepository.findByOrganizationIdAndExternalId("organizationId-0", "externalId-1"))
                .thenReturn(Optional.of(ENTITY));

        assertThat(store.findByOrganizationIdAndExternalId("organizationId-0", "externalId-1")).contains(DOMAIN);
    }

    @Test
    void findByOrganizationIdAndEmailMapsTheRowItFinds() {

        when(userRepository.findByOrganizationIdAndEmail("organizationId-0", "email-1"))
                .thenReturn(Optional.of(ENTITY));

        assertThat(store.findByOrganizationIdAndEmail("organizationId-0", "email-1")).contains(DOMAIN);
    }

    @Test
    void findPresentUserIdsAnswersTheRepositoryIds() {

        when(userRepository.findPresentUserIds("organizationId-0")).thenReturn(List.of("u-1"));

        assertThat(store.findPresentUserIds("organizationId-0")).containsExactly("u-1");
    }

    @Test
    void findActiveSublessByEmailMapsTheRowItFinds() {

        when(userRepository.findActiveSublessByEmail("organizationId-0", "email-1")).thenReturn(Optional.of(ENTITY));

        assertThat(store.findActiveSublessByEmail("organizationId-0", "email-1")).contains(DOMAIN);
    }

    @Test
    void findActiveByEmailMapsTheRowItFinds() {

        when(userRepository.findActiveByEmail("organizationId-0", "email-1")).thenReturn(Optional.of(ENTITY));

        assertThat(store.findActiveByEmail("organizationId-0", "email-1")).contains(DOMAIN);
    }

    @Test
    void searchMapsEveryRowItFinds() {

        when(userRepository.search(UserSearch.builder().organizationId("org").build())).thenReturn(List.of(ENTITY));

        assertThat(store.search(UserSearch.builder().organizationId("org").build())).containsExactly(DOMAIN);
    }

    @Test
    void findTimeZonesAnswersTheRepositoryRows() {

        var row = new UserTimeZoneRow("u-1", "Europe/Brussels");
        when(userRepository.findTimeZones("organizationId-0", List.of("a", "b"))).thenReturn(List.of(row));

        assertThat(store.findTimeZones("organizationId-0", List.of("a", "b"))).containsExactly(row);
    }

    @Test
    void saveWritesTheMappedRowAndAnswersWhatWasStored() {

        when(userRepository.save(any())).thenReturn(ENTITY);

        var actual = store.save(DOMAIN);

        var written = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(written.capture());
        assertThat(written.getValue()).usingRecursiveComparison().isEqualTo(ENTITY);
        assertThat(actual).isEqualTo(DOMAIN);
    }

    @Test
    void attachSubDelegatesToTheRepository() {

        store.attachSub(10L, "userId-1", Instant.parse("2026-03-03T10:00:00Z"));

        verify(userRepository).attachSub(10L, "userId-1", Instant.parse("2026-03-03T10:00:00Z"));
    }

    @Test
    void upsertDelegatesToTheRepository() {

        store.upsert(UserUpsert.builder().organizationId("org").build(), Instant.parse("2026-02-02T10:00:00Z"));

        verify(userRepository).upsert(UserUpsert.builder().organizationId("org").build(),
                Instant.parse("2026-02-02T10:00:00Z"));
    }

    @Test
    void updateSyncedAvatarDelegatesToTheRepository() {

        store.updateSyncedAvatar(AvatarSyncUpdate.builder().organizationId("org").build());

        verify(userRepository).updateSyncedAvatar(AvatarSyncUpdate.builder().organizationId("org").build());
    }

    @Test
    void touchAvatarSyncedAtDelegatesToTheRepository() {

        store.touchAvatarSyncedAt("organizationId-0", "userId-1", Instant.parse("2026-03-03T10:00:00Z"));

        verify(userRepository).touchAvatarSyncedAt("organizationId-0", "userId-1",
                Instant.parse("2026-03-03T10:00:00Z"));
    }

    @Test
    void clearSyncedAvatarDelegatesToTheRepository() {

        store.clearSyncedAvatar("organizationId-0", "userId-1", Instant.parse("2026-03-03T10:00:00Z"));

        verify(userRepository).clearSyncedAvatar("organizationId-0", "userId-1", Instant.parse("2026-03-03T10:00:00Z"));
    }

    @Test
    void updateAvatarOverrideDelegatesToTheRepository() {

        store.updateAvatarOverride(AvatarOverrideUpdate.builder().organizationId("org").build(),
                Instant.parse("2026-02-02T10:00:00Z"));

        verify(userRepository).updateAvatarOverride(AvatarOverrideUpdate.builder().organizationId("org").build(),
                Instant.parse("2026-02-02T10:00:00Z"));
    }

    @Test
    void clearAvatarOverrideDelegatesToTheRepository() {

        store.clearAvatarOverride("organizationId-0", "userId-1", Instant.parse("2026-03-03T10:00:00Z"));

        verify(userRepository).clearAvatarOverride("organizationId-0", "userId-1",
                Instant.parse("2026-03-03T10:00:00Z"));
    }

    @Test
    void updateObservedTimeZoneDelegatesToTheRepository() {

        store.updateObservedTimeZone("organizationId-0", "userId-1", "zone-2", Instant.parse("2026-04-04T10:00:00Z"));

        verify(userRepository).updateObservedTimeZone("organizationId-0", "userId-1", "zone-2",
                Instant.parse("2026-04-04T10:00:00Z"));
    }

    @Test
    void updateDeclaredTimeZoneDelegatesToTheRepository() {

        store.updateDeclaredTimeZone("organizationId-0", "userId-1", "zone-2", Instant.parse("2026-04-04T10:00:00Z"));

        verify(userRepository).updateDeclaredTimeZone("organizationId-0", "userId-1", "zone-2",
                Instant.parse("2026-04-04T10:00:00Z"));
    }

    @Test
    void clearDeclaredTimeZoneDelegatesToTheRepository() {

        store.clearDeclaredTimeZone("organizationId-0", "userId-1", Instant.parse("2026-03-03T10:00:00Z"));

        verify(userRepository).clearDeclaredTimeZone("organizationId-0", "userId-1",
                Instant.parse("2026-03-03T10:00:00Z"));
    }
}
