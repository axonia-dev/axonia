package dev.axonia.organization.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.organization.api.Organization;
import dev.axonia.organization.api.OrganizationKind;
import dev.axonia.organization.api.OrganizationLifecycleState;
import dev.axonia.organization.api.OrganizationRegistration;
import dev.axonia.organization.api.spi.OrganizationWriteGate;
import dev.axonia.organization.application.port.out.OrganizationContext;
import dev.axonia.organization.application.port.out.OrganizationStore;
import dev.axonia.organization.domain.RegistryEntry;
import dev.axonia.organization.domain.RegistryEntryMother;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;

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
    private OrganizationStore store;

    @Mock
    private OrganizationWriteGate writeGate;

    @Mock
    private OrganizationContext organizationContext;

    private OrganizationService sut;

    @BeforeEach
    void setUp() {
        sut = new OrganizationService(store, writeGate, organizationContext, FIXED_CLOCK);
    }

    @Test
    void registerStoresAnOrganizationOfTheGivenKindWithItsOwnName() {

        // given
        var registration = OrganizationRegistration.builder().id("northwind").name("Northwind")
                .kind(OrganizationKind.ENTERPRISE).build();
        when(store.register(registration)).thenReturn(
                RegistryEntryMother.any().id("northwind").name("Northwind").kind(OrganizationKind.ENTERPRISE).build());

        // when
        var actual = sut.register(registration);

        // then
        verify(store).register(registration);
        assertThat(actual.id()).isEqualTo("northwind");
    }

    @Test
    void registerAnswersTheFullyMappedModel() {

        // given
        when(store.register(any())).thenReturn(RegistryEntryMother.any().id("rust-practitioners")
                .name("Rust Practitioners").kind(OrganizationKind.HUB).build());

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
        var entry = RegistryEntryMother.any().id("rust-hub").name("Rust Hub").kind(OrganizationKind.HUB).build();
        when(store.find("rust-hub")).thenReturn(Optional.of(entry));
        when(store.update(any())).thenAnswer(inv -> inv.getArgument(0, RegistryEntry.class));

        // when
        sut.rename("rust-hub", "Rustaceans");

        // then - an update of the existing row
        verify(store).update(argThat(
                e -> "rust-hub".equals(e.id()) && "Rustaceans".equals(e.name()) && e.kind() == OrganizationKind.HUB));
    }

    @Test
    void renameOfAMissingRowIsABrokenInvariant() {

        // given
        when(store.find("ghost")).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> sut.rename("ghost", "Ghost")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void organizationExists_delegatesToTheStore() {

        // given
        when(store.exists("acme")).thenReturn(true);

        // when / then
        assertThat(sut.organizationExists("acme")).isTrue();
    }

    @Test
    void organization_found_returnsFullyMappedModel() {

        // given
        var entry = RegistryEntryMother.any().id("hub-a").name("Alpha").kind(OrganizationKind.HUB).build();
        when(store.find("hub-a")).thenReturn(Optional.of(entry));

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
        when(store.find("unknown")).thenReturn(Optional.empty());

        // when
        var actual = sut.organization("unknown");

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void deleteRemovesTheRegistryRowOfTheExpectedKind() {

        // given
        when(store.find("rust-practitioners")).thenReturn(Optional.of(hubEntry("rust-practitioners")));

        // when
        sut.delete("rust-practitioners", OrganizationKind.HUB);

        // then
        verify(store).delete("rust-practitioners");
    }

    @Test
    void deleteRefusesAnOrganizationOfAnotherKind() {

        // given
        when(store.find("acme")).thenReturn(Optional.of(enterpriseEntry("acme")));

        // when / then
        assertThatIllegalStateException().isThrownBy(() -> sut.delete("acme", OrganizationKind.HUB))
                .withMessageContaining("acme");
        verify(store, never()).delete(any());
    }

    @Test
    void deleteRefusesAMissingOrganization() {

        // given
        when(store.find("ghost")).thenReturn(Optional.empty());

        // when / then
        assertThatIllegalStateException().isThrownBy(() -> sut.delete("ghost", OrganizationKind.HUB));
    }

    @Test
    void suspendMovesActiveToSuspended() {

        // given
        when(store.find("acme")).thenReturn(Optional.of(enterpriseEntry("acme", OrganizationLifecycleState.ACTIVE)));
        when(store.update(any())).thenAnswer(inv -> inv.getArgument(0, RegistryEntry.class));
        var saved = ArgumentCaptor.forClass(RegistryEntry.class);

        // when
        sut.suspend("acme");

        // then
        verify(store).update(saved.capture());
        assertThat(saved.getValue().lifecycleState()).isEqualTo(OrganizationLifecycleState.SUSPENDED);
    }

    @Test
    void unsuspendMovesSuspendedToActive() {

        // given
        when(store.find("acme")).thenReturn(Optional.of(enterpriseEntry("acme", OrganizationLifecycleState.SUSPENDED)));
        when(store.update(any())).thenAnswer(inv -> inv.getArgument(0, RegistryEntry.class));
        var saved = ArgumentCaptor.forClass(RegistryEntry.class);

        // when
        sut.unsuspend("acme");

        // then
        verify(store).update(saved.capture());
        assertThat(saved.getValue().lifecycleState()).isEqualTo(OrganizationLifecycleState.ACTIVE);
    }

    @Test
    void suspendRejectsClosedOrganization() {

        // given
        when(store.find("acme")).thenReturn(Optional.of(enterpriseEntry("acme", OrganizationLifecycleState.CLOSED)));

        // when / then
        assertThatThrownBy(() -> sut.suspend("acme")).isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode()).isEqualTo("invalid_lifecycle_transition");
        verify(store, never()).update(any());
    }

    /** The registry moves any kind; which kinds may move is the caller's rule. */
    @Test
    void suspendIsKindNeutral() {

        // given
        when(store.find("rust-hub")).thenReturn(Optional.of(hubEntry("rust-hub")));
        when(store.update(any())).thenAnswer(inv -> inv.getArgument(0, RegistryEntry.class));
        var saved = ArgumentCaptor.forClass(RegistryEntry.class);

        // when
        sut.suspend("rust-hub");

        // then
        verify(store).update(saved.capture());
        assertThat(saved.getValue().lifecycleState()).isEqualTo(OrganizationLifecycleState.SUSPENDED);
    }

    @Test
    void suspendRejectsUnknownOrganization() {

        // given
        when(store.find("ghost")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.suspend("ghost")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void requireClosableRejectsActiveOrganization() {

        // given
        when(store.find("acme")).thenReturn(Optional.of(enterpriseEntry("acme", OrganizationLifecycleState.ACTIVE)));

        // when / then
        assertThatThrownBy(() -> sut.requireClosable("acme")).isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode()).isEqualTo("invalid_lifecycle_transition");
    }

    @Test
    void requireClosableAcceptsSuspendedAndClosedOrganizations() {

        // given
        when(store.find("sus")).thenReturn(Optional.of(enterpriseEntry("sus", OrganizationLifecycleState.SUSPENDED)));
        when(store.find("clo")).thenReturn(Optional.of(enterpriseEntry("clo", OrganizationLifecycleState.CLOSED)));

        // when / then
        assertThat(sut.requireClosable("sus").lifecycleState()).isEqualTo(OrganizationLifecycleState.SUSPENDED);
        assertThat(sut.requireClosable("clo").lifecycleState()).isEqualTo(OrganizationLifecycleState.CLOSED);
    }

    @Test
    void requireClosableRejectsUnknownOrganization() {

        // given
        when(store.find("ghost")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.requireClosable("ghost")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void markClosedStampsClosedStateTimestampAndOperator() {

        // given
        when(store.find("acme")).thenReturn(Optional.of(enterpriseEntry("acme", OrganizationLifecycleState.SUSPENDED)));
        when(store.update(any())).thenAnswer(inv -> inv.getArgument(0, RegistryEntry.class));
        var saved = ArgumentCaptor.forClass(RegistryEntry.class);

        // when
        var actual = sut.markClosed("acme", "op-1");

        // then
        verify(store).update(saved.capture());
        assertThat(saved.getValue().lifecycleState()).isEqualTo(OrganizationLifecycleState.CLOSED);
        assertThat(saved.getValue().closedAt()).isEqualTo(FIXED_CLOCK.instant());
        assertThat(saved.getValue().closedBy()).isEqualTo("op-1");
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
        when(store.setCommunicationsEnabled("acme", false, FIXED_CLOCK.instant())).thenReturn(1);
        when(store.find("acme")).thenReturn(Optional.of(RegistryEntryMother.any().id("acme").name("Acme Corp")
                .kind(OrganizationKind.ENTERPRISE).communicationsEnabled(false).build()));

        // when
        var actual = sut.setCommunicationsEnabled(false);

        // then
        verify(writeGate).requireCommunicationWrite();
        verify(writeGate, never()).requireWrite();
        verify(store).setCommunicationsEnabled("acme", false, FIXED_CLOCK.instant());
        assertThat(actual.communicationsEnabled()).isFalse();
    }

    @Test
    void setCommunicationsEnabled_checksThePermissionBeforeWriting() {

        // given: a mock that never throws would pass this test just as well if the check ran after the write, so
        // this makes the gate actually reject the call rather than merely asserting it was invoked
        doThrow(new ForbiddenException("missing permission")).when(writeGate).requireCommunicationWrite();

        // when / then
        assertThatThrownBy(() -> sut.setCommunicationsEnabled(false)).isInstanceOf(ForbiddenException.class);
        verify(store, never()).setCommunicationsEnabled(any(), anyBoolean(), any());
    }

    @Test
    void setCommunicationsEnabled_staleOrganizationThrowsNotFound() {

        // given: a token scoped to an id the registry no longer has, e.g. a deleted enterprise
        when(organizationContext.organizationId()).thenReturn("ghost");
        when(store.setCommunicationsEnabled("ghost", false, FIXED_CLOCK.instant())).thenReturn(0);

        // when / then: a zero-row update must not look like success
        assertThatThrownBy(() -> sut.setCommunicationsEnabled(false)).isInstanceOf(NotFoundException.class);
        verify(store, never()).find("ghost");
    }

    private RegistryEntry hubEntry(String id) {
        return RegistryEntryMother.any().id(id).name(id).kind(OrganizationKind.HUB).build();
    }

    private RegistryEntry enterpriseEntry(String id) {
        return RegistryEntryMother.any().id(id).name(id).kind(OrganizationKind.ENTERPRISE).build();
    }

    private RegistryEntry enterpriseEntry(String id, OrganizationLifecycleState state) {
        return enterpriseEntry(id).toBuilder().lifecycleState(state).build();
    }
}
