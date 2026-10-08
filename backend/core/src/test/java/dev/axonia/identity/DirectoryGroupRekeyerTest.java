package dev.axonia.identity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.identity.internal.DirectoryGroupEntity;
import dev.axonia.identity.internal.DirectoryGroupRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class DirectoryGroupRekeyerTest {

    private static final Instant NOW = Instant.parse("2026-08-28T08:00:00Z");

    @Mock
    private DirectoryGroupRepository repository;

    @Mock
    private DirectoryGroupClaims directoryGroupClaims;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private DirectoryGroupRekeyer sut;

    @BeforeEach
    void buildRekeyer() {

        sut = new DirectoryGroupRekeyer(repository, directoryGroupClaims, clock);
    }

    @Test
    void rekeyTo_entra_rewritesKeysToExternalId() {

        // given
        when(repository.countGroupsWithoutExternalId("acme")).thenReturn(0);
        when(repository.rekeyToExternalId("acme")).thenReturn(3);

        // when
        sut.rekeyTo("acme", IdpProvider.ENTRA);

        // then
        verify(repository).rekeyToExternalId("acme");
        verify(repository, never()).rekeyToDisplayName("acme");
    }

    @Test
    void rekeyTo_entraWithGroupsMissingAnExternalId_leavesThoseRowsAlone() {

        // given - external_id is null for some rows; reconciliation_key is NOT NULL and a null key would deny
        // every role bound to those groups, so those rows keep their display-name key and the gap is logged
        when(repository.countGroupsWithoutExternalId("acme")).thenReturn(2);
        when(repository.rekeyToExternalId("acme")).thenReturn(1);

        // when
        sut.rekeyTo("acme", IdpProvider.ENTRA);

        // then
        verify(repository).countGroupsWithoutExternalId("acme");
        verify(repository).rekeyToExternalId("acme");
    }

    @Test
    void rekeyTo_nonEntra_rewritesKeysToDisplayName() {

        // given
        when(repository.rekeyToDisplayName("acme")).thenReturn(4);

        // when
        sut.rekeyTo("acme", IdpProvider.GENERIC_OIDC);

        // then
        verify(repository).rekeyToDisplayName("acme");
        verify(repository, never()).rekeyToExternalId("acme");
    }

    @Test
    void rekeyTo_holdsOnlyTheGroupsItMovesOntoAKeyARoleBindingIsWaitingOn() {

        // given - two confirmed groups the switch to Entra moves. One lands on a key an Owner binding is already
        // waiting on, which must be held; the other lands on a key nobody has ever bound.
        when(repository.countGroupsWithoutExternalId("acme")).thenReturn(0);
        when(repository.rekeyToExternalId("acme")).thenReturn(2);
        when(repository.findConfirmedGroupsRekeyingToExternalId("acme"))
                .thenReturn(List.of(group(1L, "platform-admins", "Foo"), group(2L, "obj-id-2", "Marketing")));
        when(directoryGroupClaims.isClaimedByARoleBinding("acme", "platform-admins")).thenReturn(true);
        when(directoryGroupClaims.isClaimedByARoleBinding("acme", "obj-id-2")).thenReturn(false);

        // when
        sut.rekeyTo("acme", IdpProvider.ENTRA);

        // then - only the dangerous landing loses its confirmation; holding both would put an Organization's whole
        // directory behind a confirmation nothing was ever waiting for
        verify(repository).holdByIds("acme", List.of(1L), NOW);
    }

    @Test
    void rekeyTo_whenNothingLandsOnAClaimedKey_holdsNothingAtAll() {

        // given
        when(repository.countGroupsWithoutExternalId("acme")).thenReturn(0);
        when(repository.rekeyToExternalId("acme")).thenReturn(1);
        when(repository.findConfirmedGroupsRekeyingToExternalId("acme"))
                .thenReturn(List.of(group(1L, "obj-id-1", "Marketing")));
        when(directoryGroupClaims.isClaimedByARoleBinding("acme", "obj-id-1")).thenReturn(false);

        // when
        sut.rekeyTo("acme", IdpProvider.ENTRA);

        // then - and no empty IN () statement is sent either
        verify(repository, never()).holdByIds(any(), any(), any());
    }

    @Test
    void rekeyTo_nonEntra_holdsAGroupItMovesOntoAClaimedDisplayName() {

        // given - the mirror direction, where the key follows displayName
        when(repository.rekeyToDisplayName("acme")).thenReturn(1);
        when(repository.findConfirmedGroupsRekeyingToDisplayName("acme"))
                .thenReturn(List.of(group(9L, "obj-id-9", "platform-admins")));
        when(directoryGroupClaims.isClaimedByARoleBinding("acme", "platform-admins")).thenReturn(true);

        // when
        sut.rekeyTo("acme", IdpProvider.GENERIC_OIDC);

        // then
        verify(repository).holdByIds(eq("acme"), eq(List.of(9L)), any());
    }

    private static DirectoryGroupEntity group(Long id, String externalId, String displayName) {

        return DirectoryGroupEntity.builder().id(id).organizationId("acme").externalId(externalId)
                .displayName(displayName).reconciliationKey("current-key-" + id).confirmedAt(NOW).build();
    }
}
