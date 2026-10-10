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

import dev.axonia.identity.domain.DirectoryGroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcDirectoryGroupStoreTest {

    private static final DirectoryGroupEntity ENTITY = DirectoryGroupEntity.builder().id(10L)
            .organizationId("organizationId-1").scimId("scimId-2").externalId("externalId-3")
            .displayName("displayName-4").reconciliationKey("reconciliationKey-5")
            .confirmedAt(Instant.parse("2026-07-07T10:00:00Z")).createdAt(Instant.parse("2026-08-08T10:00:00Z"))
            .updatedAt(Instant.parse("2026-09-09T10:00:00Z")).version(19L).build();

    private static final DirectoryGroup DOMAIN = JdbcDirectoryGroupStore.toDirectoryGroup(ENTITY);

    @Mock
    private DirectoryGroupRepository directoryGroupRepository;

    @InjectMocks
    private JdbcDirectoryGroupStore store;

    @Test
    void mapsEveryColumnBothWays() {

        assertThat(DOMAIN).usingRecursiveComparison().isEqualTo(ENTITY);
        assertThat(JdbcDirectoryGroupStore.toEntity(DOMAIN)).usingRecursiveComparison().isEqualTo(ENTITY);
    }

    @Test
    void findByOrganizationIdAndScimIdMapsTheRowItFinds() {

        when(directoryGroupRepository.findByOrganizationIdAndScimId("organizationId-0", "scimId-1"))
                .thenReturn(Optional.of(ENTITY));

        assertThat(store.findByOrganizationIdAndScimId("organizationId-0", "scimId-1")).contains(DOMAIN);
    }

    @Test
    void findByOrganizationIdAndReconciliationKeyMapsTheRowItFinds() {

        when(directoryGroupRepository.findByOrganizationIdAndReconciliationKey("organizationId-0",
                "reconciliationKey-1")).thenReturn(Optional.of(ENTITY));

        assertThat(store.findByOrganizationIdAndReconciliationKey("organizationId-0", "reconciliationKey-1"))
                .contains(DOMAIN);
    }

    @Test
    void findByOrganizationIdAndDisplayNameMapsEveryRowItFinds() {

        when(directoryGroupRepository.findByOrganizationIdAndDisplayName("organizationId-0", "displayName-1"))
                .thenReturn(List.of(ENTITY));

        assertThat(store.findByOrganizationIdAndDisplayName("organizationId-0", "displayName-1"))
                .containsExactly(DOMAIN);
    }

    @Test
    void findByOrganizationIdAndReconciliationKeyInMapsEveryRowItFinds() {

        when(directoryGroupRepository.findByOrganizationIdAndReconciliationKeyIn("organizationId-0", List.of("a", "b")))
                .thenReturn(List.of(ENTITY));

        assertThat(store.findByOrganizationIdAndReconciliationKeyIn("organizationId-0", List.of("a", "b")))
                .containsExactly(DOMAIN);
    }

    @Test
    void findByOrganizationIdMapsEveryRowItFinds() {

        when(directoryGroupRepository.findByOrganizationId("organizationId-0", PageRequest.of(0, 10)))
                .thenReturn(List.of(ENTITY));

        assertThat(store.findByOrganizationId("organizationId-0", 10)).containsExactly(DOMAIN);
    }

    @Test
    void findByOrganizationIdAndConfirmedAtIsNullMapsEveryRowItFinds() {

        when(directoryGroupRepository.findByOrganizationIdAndConfirmedAtIsNull("organizationId-0"))
                .thenReturn(List.of(ENTITY));

        assertThat(store.findByOrganizationIdAndConfirmedAtIsNull("organizationId-0")).containsExactly(DOMAIN);
    }

    @Test
    void findByOrganizationIdAndMemberScimIdMapsEveryRowItFinds() {

        when(directoryGroupRepository.findByOrganizationIdAndMemberScimId("organizationId-0", "userScimId-1"))
                .thenReturn(List.of(ENTITY));

        assertThat(store.findByOrganizationIdAndMemberScimId("organizationId-0", "userScimId-1"))
                .containsExactly(DOMAIN);
    }

    @Test
    void saveWritesTheMappedRowAndAnswersWhatWasStored() {

        when(directoryGroupRepository.save(any())).thenReturn(ENTITY);

        var actual = store.save(DOMAIN);

        var written = ArgumentCaptor.forClass(DirectoryGroupEntity.class);
        verify(directoryGroupRepository).save(written.capture());
        assertThat(written.getValue()).usingRecursiveComparison().isEqualTo(ENTITY);
        assertThat(actual).isEqualTo(DOMAIN);
    }

    @Test
    void deleteRemovesTheMappedRow() {

        store.delete(DOMAIN);

        var removed = ArgumentCaptor.forClass(DirectoryGroupEntity.class);
        verify(directoryGroupRepository).delete(removed.capture());
        assertThat(removed.getValue()).usingRecursiveComparison().isEqualTo(ENTITY);
    }

    @Test
    void rekeyToExternalIdAnswersTheRepositoryCount() {

        when(directoryGroupRepository.rekeyToExternalId("organizationId-0")).thenReturn(3);

        assertThat(store.rekeyToExternalId("organizationId-0")).isEqualTo(3);
    }

    @Test
    void findConfirmedGroupsRekeyingToExternalIdMapsEveryRowItFinds() {

        when(directoryGroupRepository.findConfirmedGroupsRekeyingToExternalId("organizationId-0"))
                .thenReturn(List.of(ENTITY));

        assertThat(store.findConfirmedGroupsRekeyingToExternalId("organizationId-0")).containsExactly(DOMAIN);
    }

    @Test
    void rekeyToDisplayNameAnswersTheRepositoryCount() {

        when(directoryGroupRepository.rekeyToDisplayName("organizationId-0")).thenReturn(3);

        assertThat(store.rekeyToDisplayName("organizationId-0")).isEqualTo(3);
    }

    @Test
    void findConfirmedGroupsRekeyingToDisplayNameMapsEveryRowItFinds() {

        when(directoryGroupRepository.findConfirmedGroupsRekeyingToDisplayName("organizationId-0"))
                .thenReturn(List.of(ENTITY));

        assertThat(store.findConfirmedGroupsRekeyingToDisplayName("organizationId-0")).containsExactly(DOMAIN);
    }

    @Test
    void holdByIdsAnswersTheRepositoryCount() {

        when(directoryGroupRepository.holdByIds("organizationId-0", List.of(1L, 2L),
                Instant.parse("2026-03-03T10:00:00Z"))).thenReturn(3);

        assertThat(store.holdByIds("organizationId-0", List.of(1L, 2L), Instant.parse("2026-03-03T10:00:00Z")))
                .isEqualTo(3);
    }

    @Test
    void countGroupsWithoutExternalIdAnswersTheRepositoryCount() {

        when(directoryGroupRepository.countGroupsWithoutExternalId("organizationId-0")).thenReturn(3);

        assertThat(store.countGroupsWithoutExternalId("organizationId-0")).isEqualTo(3);
    }
}
