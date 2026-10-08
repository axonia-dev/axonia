package dev.axonia.organization.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.organization.Organization;
import dev.axonia.organization.OrganizationContext;
import dev.axonia.organization.OrganizationKind;
import dev.axonia.organization.OrganizationLifecycleState;
import dev.axonia.organization.OrganizationRegistration;
import dev.axonia.organization.OrganizationService;
import dev.axonia.organization.OrganizationWriteGate;
import dev.axonia.organization.mother.OrganizationEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class OrganizationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-25T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private OrganizationRepository repository;

    @Mock
    private OrganizationWriteGate writeGate;

    @Mock
    private OrganizationContext organizationContext;

    private OrganizationService sut;

    @BeforeEach
    void setUp() {
        sut = new OrganizationService(repository, writeGate, organizationContext, FIXED_CLOCK);
    }

    @Test
    void registerStoresAnOrganizationOfTheGivenKindWithItsOwnName() {

        // given
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        var saved = ArgumentCaptor.forClass(OrganizationEntity.class);

        // when
        var actual = sut.register(OrganizationRegistration.builder().id("northwind").name("Northwind")
                .kind(OrganizationKind.ENTERPRISE).build());

        // then
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo("northwind");
        assertThat(saved.getValue().getName()).isEqualTo("Northwind");
        assertThat(saved.getValue().getKind()).isEqualTo(OrganizationKind.ENTERPRISE);
        assertThat(saved.getValue().isNew()).isTrue();
        assertThat(actual.id()).isEqualTo("northwind");
    }

    @Test
    void registerAnswersTheFullyMappedModel() {

        // given
        when(repository.save(any(OrganizationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.register(OrganizationRegistration.builder().id("rust-practitioners").name("Rust Practitioners")
                .kind(OrganizationKind.HUB).build());

        // then
        assertThat(actual).usingRecursiveComparison()
                .isEqualTo(Organization.builder().id("rust-practitioners").name("Rust Practitioners")
                        .kind(OrganizationKind.HUB).lifecycleState(OrganizationLifecycleState.ACTIVE)
                        .communicationsEnabled(true).build());
    }

    @Test
    void renameUpdatesTheNameAndLeavesTheRestUntouched() {

        // given
        var entity = OrganizationEntityMother.any().id("rust-hub").name("Rust Hub").kind(OrganizationKind.HUB).build();
        when(repository.findById("rust-hub")).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0, OrganizationEntity.class));

        // when
        sut.rename("rust-hub", "Rustaceans");

        // then - an UPDATE of the existing row (isNew=false)
        verify(repository).save(argThat(e -> !e.isNew() && "rust-hub".equals(e.getId())
                && "Rustaceans".equals(e.getName()) && e.getKind() == OrganizationKind.HUB));
    }

    @Test
    void renameOfAMissingRowIsABrokenInvariant() {

        // given
        when(repository.findById("ghost")).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> sut.rename("ghost", "Ghost")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void organizationExists_delegatesToRepository() {

        // given
        when(repository.existsById("acme")).thenReturn(true);

        // when / then
        assertThat(sut.organizationExists("acme")).isTrue();
    }

    @Test
    void organization_found_returnsFullyMappedModel() {

        // given
        var entity = OrganizationEntityMother.any().id("hub-a").name("Alpha").kind(OrganizationKind.HUB).build();
        when(repository.findById("hub-a")).thenReturn(Optional.of(entity));

        // when
        var actual = sut.organization("hub-a");

        // then
        assertThat(actual).isPresent();
        assertThat(actual.orElseThrow()).usingRecursiveComparison()
                .isEqualTo(Organization.builder().id("hub-a").name("Alpha").kind(OrganizationKind.HUB)
                        .lifecycleState(OrganizationLifecycleState.ACTIVE).communicationsEnabled(true).build());
    }

    @Test
    void organization_unknownId_returnsEmpty() {

        // given
        when(repository.findById("unknown")).thenReturn(Optional.empty());

        // when
        var actual = sut.organization("unknown");

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void deleteRemovesTheRegistryRowOfTheExpectedKind() {

        // given
        when(repository.findById("rust-practitioners")).thenReturn(Optional.of(hubEntity("rust-practitioners")));

        // when
        sut.delete("rust-practitioners", OrganizationKind.HUB);

        // then
        verify(repository).deleteById("rust-practitioners");
    }

    @Test
    void deleteRefusesAnOrganizationOfAnotherKind() {

        // given
        when(repository.findById("acme")).thenReturn(Optional.of(enterpriseEntity("acme")));

        // when / then
        assertThatIllegalStateException().isThrownBy(() -> sut.delete("acme", OrganizationKind.HUB))
                .withMessageContaining("acme");
        verify(repository, never()).deleteById(any());
    }

    @Test
    void deleteRefusesAMissingOrganization() {

        // given
        when(repository.findById("ghost")).thenReturn(Optional.empty());

        // when / then
        assertThatIllegalStateException().isThrownBy(() -> sut.delete("ghost", OrganizationKind.HUB));
    }

    @Test
    void suspendMovesActiveToSuspended() {

        // given
        when(repository.findById("acme"))
                .thenReturn(Optional.of(enterpriseEntity("acme", OrganizationLifecycleState.ACTIVE)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0, OrganizationEntity.class));
        var saved = ArgumentCaptor.forClass(OrganizationEntity.class);

        // when
        sut.suspend("acme");

        // then
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getLifecycleState()).isEqualTo(OrganizationLifecycleState.SUSPENDED);
    }

    @Test
    void unsuspendMovesSuspendedToActive() {

        // given
        when(repository.findById("acme"))
                .thenReturn(Optional.of(enterpriseEntity("acme", OrganizationLifecycleState.SUSPENDED)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0, OrganizationEntity.class));
        var saved = ArgumentCaptor.forClass(OrganizationEntity.class);

        // when
        sut.unsuspend("acme");

        // then
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getLifecycleState()).isEqualTo(OrganizationLifecycleState.ACTIVE);
    }

    @Test
    void suspendRejectsClosedOrganization() {

        // given
        when(repository.findById("acme"))
                .thenReturn(Optional.of(enterpriseEntity("acme", OrganizationLifecycleState.CLOSED)));

        // when / then
        assertThatThrownBy(() -> sut.suspend("acme")).isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode()).isEqualTo("invalid_lifecycle_transition");
        verify(repository, never()).save(any());
    }

    /** The registry moves any kind; which kinds may move is the caller's rule. */
    @Test
    void suspendIsKindNeutral() {

        // given
        when(repository.findById("rust-hub")).thenReturn(Optional.of(hubEntity("rust-hub")));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0, OrganizationEntity.class));
        var saved = ArgumentCaptor.forClass(OrganizationEntity.class);

        // when
        sut.suspend("rust-hub");

        // then
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getLifecycleState()).isEqualTo(OrganizationLifecycleState.SUSPENDED);
    }

    @Test
    void suspendRejectsUnknownOrganization() {

        // given
        when(repository.findById("ghost")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.suspend("ghost")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void requireClosableRejectsActiveOrganization() {

        // given
        when(repository.findById("acme"))
                .thenReturn(Optional.of(enterpriseEntity("acme", OrganizationLifecycleState.ACTIVE)));

        // when / then
        assertThatThrownBy(() -> sut.requireClosable("acme")).isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode()).isEqualTo("invalid_lifecycle_transition");
    }

    @Test
    void requireClosableAcceptsSuspendedAndClosedOrganizations() {

        // given
        when(repository.findById("sus"))
                .thenReturn(Optional.of(enterpriseEntity("sus", OrganizationLifecycleState.SUSPENDED)));
        when(repository.findById("clo"))
                .thenReturn(Optional.of(enterpriseEntity("clo", OrganizationLifecycleState.CLOSED)));

        // when / then
        assertThat(sut.requireClosable("sus").lifecycleState()).isEqualTo(OrganizationLifecycleState.SUSPENDED);
        assertThat(sut.requireClosable("clo").lifecycleState()).isEqualTo(OrganizationLifecycleState.CLOSED);
    }

    @Test
    void requireClosableRejectsUnknownOrganization() {

        // given
        when(repository.findById("ghost")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.requireClosable("ghost")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void markClosedStampsClosedStateTimestampAndOperator() {

        // given
        when(repository.findById("acme"))
                .thenReturn(Optional.of(enterpriseEntity("acme", OrganizationLifecycleState.SUSPENDED)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0, OrganizationEntity.class));
        var saved = ArgumentCaptor.forClass(OrganizationEntity.class);

        // when
        var actual = sut.markClosed("acme", "op-1");

        // then
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getLifecycleState()).isEqualTo(OrganizationLifecycleState.CLOSED);
        assertThat(saved.getValue().getClosedAt()).isEqualTo(FIXED_CLOCK.instant());
        assertThat(saved.getValue().getClosedBy()).isEqualTo("op-1");
        assertThat(actual.lifecycleState()).isEqualTo(OrganizationLifecycleState.CLOSED);
    }

    /**
     * Not {@code organization:write}, which is what it used to be: silencing every community's mail across a tenant is
     * not the same authority as renaming the account, and while it rode on the generic write right anyone who could
     * edit the workspace profile could also stop every message it sends.
     */
    @Test
    void turningCommunicationsOffRequiresCommunicationWrite() {

        // given
        when(organizationContext.organizationId()).thenReturn("acme");
        when(repository.updateCommunicationsEnabled("acme", false, FIXED_CLOCK.instant())).thenReturn(1);
        when(repository.findById("acme")).thenReturn(Optional.of(OrganizationEntityMother.any().id("acme")
                .name("Acme Corp").kind(OrganizationKind.ENTERPRISE).communicationsEnabled(false).build()));

        // when
        var actual = sut.setCommunicationsEnabled(false);

        // then
        verify(writeGate).requireCommunicationWrite();
        verify(writeGate, never()).requireWrite();
        verify(repository).updateCommunicationsEnabled("acme", false, FIXED_CLOCK.instant());
        assertThat(actual.communicationsEnabled()).isFalse();
    }

    @Test
    void setCommunicationsEnabled_checksThePermissionBeforeWriting() {

        // given: a mock that never throws would pass this test just as well if the check ran after the write, so
        // this makes the gate actually reject the call rather than merely asserting it was invoked
        doThrow(new ForbiddenException("missing permission")).when(writeGate).requireCommunicationWrite();

        // when / then
        assertThatThrownBy(() -> sut.setCommunicationsEnabled(false)).isInstanceOf(ForbiddenException.class);
        verify(repository, never()).updateCommunicationsEnabled(any(), anyBoolean(), any());
    }

    @Test
    void setCommunicationsEnabled_staleOrganizationThrowsNotFound() {

        // given: a token scoped to an id the registry no longer has, e.g. a deleted enterprise
        when(organizationContext.organizationId()).thenReturn("ghost");
        when(repository.updateCommunicationsEnabled("ghost", false, FIXED_CLOCK.instant())).thenReturn(0);

        // when / then: a zero-row update must not look like success
        assertThatThrownBy(() -> sut.setCommunicationsEnabled(false)).isInstanceOf(NotFoundException.class);
        verify(repository, never()).findById("ghost");
    }

    @Test
    void organizationsShipWithCommunicationsOn() {

        // given / when
        var entity = OrganizationEntityMother.any().build();

        // then
        assertThat(entity.isCommunicationsEnabled()).isTrue();
    }

    private OrganizationEntity hubEntity(String id) {
        return OrganizationEntity.builder().id(id).name(id).kind(OrganizationKind.HUB).build();
    }

    private OrganizationEntity enterpriseEntity(String id) {
        return OrganizationEntity.builder().id(id).name(id).kind(OrganizationKind.ENTERPRISE).build();
    }

    private OrganizationEntity enterpriseEntity(String id, OrganizationLifecycleState state) {
        return enterpriseEntity(id).toBuilder().lifecycleState(state).build();
    }
}
