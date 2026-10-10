package dev.axonia.roundup.adapter.out.persistence;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.roundup.domain.RoundupSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcRoundupSettingsStoreTest {

    @Mock
    private RoundupSettingsRepository repository;

    @Test
    void find_readsTheStoredFlag() {

        // given
        when(repository.findById("acme"))
                .thenReturn(Optional.of(RoundupSettingsEntity.builder().organizationId("acme").enabled(true).build()));

        // when / then
        assertThat(new JdbcRoundupSettingsStore(repository).find("acme")).contains(new RoundupSettings("acme", true));
    }

    @Test
    void find_answersEmptyWhenNoRowExists() {

        // given
        when(repository.findById("acme")).thenReturn(Optional.empty());

        // when / then
        assertThat(new JdbcRoundupSettingsStore(repository).find("acme")).isEmpty();
    }

    @Test
    void save_insertsTheFirstRow() {

        // given
        when(repository.findById("acme")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = new JdbcRoundupSettingsStore(repository).save("acme", true);

        // then
        var written = ArgumentCaptor.forClass(RoundupSettingsEntity.class);
        verify(repository).save(written.capture());
        assertThat(written.getValue().isNew()).isTrue();
        assertThat(written.getValue().getOrganizationId()).isEqualTo("acme");
        assertThat(actual).isEqualTo(new RoundupSettings("acme", true));
    }

    @Test
    void save_updatesAnExistingRowInPlace() {

        // given
        when(repository.findById("acme")).thenReturn(
                Optional.of(RoundupSettingsEntity.builder().organizationId("acme").enabled(true).version(3L).build()));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = new JdbcRoundupSettingsStore(repository).save("acme", false);

        // then
        var written = ArgumentCaptor.forClass(RoundupSettingsEntity.class);
        verify(repository).save(written.capture());
        assertThat(written.getValue().isNew()).isFalse();
        assertThat(written.getValue().getVersion()).isEqualTo(3L);
        assertThat(actual).isEqualTo(new RoundupSettings("acme", false));
    }
}
