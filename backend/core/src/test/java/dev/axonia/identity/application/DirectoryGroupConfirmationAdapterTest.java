package dev.axonia.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.identity.application.port.out.DirectoryGroupStore;
import dev.axonia.identity.domain.DirectoryGroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class DirectoryGroupConfirmationAdapterTest {

    private static final String ORGANIZATION = "acme";
    private static final Instant NOW = Instant.parse("2026-08-27T11:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-08-01T06:00:00Z");

    @Mock
    private DirectoryGroupStore repository;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private DirectoryGroupConfirmationAdapter sut;

    @BeforeEach
    void buildAdapter() {

        sut = new DirectoryGroupConfirmationAdapter(repository, clock);
    }

    @Test
    void keysAwaitingConfirmation_returnsTheKeysOfTheHeldGroups() {

        // given
        when(repository.findByOrganizationIdAndConfirmedAtIsNull(ORGANIZATION))
                .thenReturn(List.of(group("platform-admins", null), group("data-leads", null)));

        // when
        var actual = sut.keysAwaitingConfirmation(ORGANIZATION);

        // then
        assertThat(actual).containsExactlyInAnyOrder("platform-admins", "data-leads");
    }

    @Test
    void keysAwaitingConfirmation_groupWithoutAKey_isExcluded() {

        // given - reconciliation_key is NOT NULL today, so this only guards that relaxing it cannot leak a null key
        when(repository.findByOrganizationIdAndConfirmedAtIsNull(ORGANIZATION)).thenReturn(List.of(group(null, null)));

        // when
        var actual = sut.keysAwaitingConfirmation(ORGANIZATION);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void confirm_stampsTheHeldGroupFromTheClock() {

        // given
        when(repository.findByOrganizationIdAndReconciliationKey(ORGANIZATION, "platform-admins"))
                .thenReturn(Optional.of(group("platform-admins", null)));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        var actual = sut.confirm(ORGANIZATION, "platform-admins");

        // then - never a SQL now(); the application drives time
        assertThat(actual).isTrue();
        var captor = ArgumentCaptor.forClass(DirectoryGroup.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().confirmedAt()).isEqualTo(NOW);
    }

    @Test
    void confirm_anAlreadyConfirmedGroup_keepsItsOriginalStamp() {

        // given - confirmed_at records when an administrator vouched, and a second click is not a second vouching
        when(repository.findByOrganizationIdAndReconciliationKey(ORGANIZATION, "platform-admins"))
                .thenReturn(Optional.of(group("platform-admins", EARLIER)));

        // when
        var actual = sut.confirm(ORGANIZATION, "platform-admins");

        // then
        assertThat(actual).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    void confirm_whenNoGroupCarriesThatKey_answersFalse() {

        // given - the ordinary state of a binding authored before the directory ever pushed the group
        when(repository.findByOrganizationIdAndReconciliationKey(ORGANIZATION, "platform-admins"))
                .thenReturn(Optional.empty());

        // when
        var actual = sut.confirm(ORGANIZATION, "platform-admins");

        // then
        assertThat(actual).isFalse();
        verify(repository, never()).save(any());
    }

    private static DirectoryGroup group(String reconciliationKey, Instant confirmedAt) {

        return DirectoryGroup.builder().id(1L).organizationId(ORGANIZATION).reconciliationKey(reconciliationKey)
                .confirmedAt(confirmedAt).build();
    }
}
