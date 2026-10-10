package dev.axonia.organization.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.organization.api.OrganizationKind;
import dev.axonia.organization.api.OrganizationLifecycleState;
import dev.axonia.organization.api.OrganizationRegistration;
import dev.axonia.organization.domain.RegistryEntry;
import dev.axonia.organization.domain.RegistryEntryMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcOrganizationStoreTest {

    private static final RegistryEntry STORED = RegistryEntryMother.any().id("acme").name("Acme Corp")
            .lifecycleState(OrganizationLifecycleState.CLOSED).closedAt(Instant.parse("2026-07-01T00:00:00Z"))
            .closedBy("op-1").communicationsEnabled(false).createdAt(Instant.parse("2026-06-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-07-01T00:00:00Z")).build();

    @Mock
    private OrganizationRepository repository;

    @InjectMocks
    private JdbcOrganizationStore sut;

    @Test
    void registerInsertsAFreshRowOfTheGivenKindWithItsOwnName() {

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
    void aRegisteredOrganizationIsActiveWithCommunicationsOn() {

        // given
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        var actual = sut.register(OrganizationRegistration.builder().id("rust-practitioners").name("Rust Practitioners")
                .kind(OrganizationKind.HUB).build());

        // then
        assertThat(actual.lifecycleState()).isEqualTo(OrganizationLifecycleState.ACTIVE);
        assertThat(actual.communicationsEnabled()).isTrue();
    }

    @Test
    void existsDelegatesToTheRepository() {

        // given
        when(repository.existsById("acme")).thenReturn(true);

        // when / then
        assertThat(sut.exists("acme")).isTrue();
    }

    @Test
    void findMapsEveryColumn() {

        // given
        when(repository.findById("acme")).thenReturn(Optional.of(JdbcOrganizationStore.toEntity(STORED)));

        // when
        var actual = sut.find("acme");

        // then
        assertThat(actual).contains(STORED);
    }

    @Test
    void findOfAnUnknownIdIsEmpty() {

        // given
        when(repository.findById("ghost")).thenReturn(Optional.empty());

        // when / then
        assertThat(sut.find("ghost")).isEmpty();
    }

    @Test
    void updateWritesBackTheExistingRow() {

        // given
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        var saved = ArgumentCaptor.forClass(OrganizationEntity.class);

        // when
        var actual = sut.update(STORED);

        // then - an UPDATE of the existing row (isNew=false), every column carried
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().isNew()).isFalse();
        assertThat(JdbcOrganizationStore.toEntry(saved.getValue())).isEqualTo(STORED);
        assertThat(actual).isEqualTo(STORED);
    }

    @Test
    void setCommunicationsEnabledAnswersTheAffectedRowCount() {

        // given
        var at = Instant.parse("2026-07-25T10:00:00Z");
        when(repository.updateCommunicationsEnabled("acme", false, at)).thenReturn(1);

        // when / then
        assertThat(sut.setCommunicationsEnabled("acme", false, at)).isEqualTo(1);
    }

    @Test
    void deleteRemovesTheRow() {

        // when
        sut.delete("acme");

        // then
        verify(repository).deleteById("acme");
    }

    @Test
    void organizationsShipWithCommunicationsOn() {

        // given / when
        var entity = OrganizationEntityMother.any().build();

        // then
        assertThat(entity.isCommunicationsEnabled()).isTrue();
    }
}
