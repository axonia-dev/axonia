package dev.axonia.roundup.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Persistable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two things {@link Persistable} is here to say, neither of which Spring Data JDBC can work out for itself on a key
 * the caller supplies: what the row's key is, and whether this instance is a row that does not exist yet.
 */
class RoundupSettingsEntityTest {

    @Test
    void theRowsKeyIsItsOrganization() {

        var entity = RoundupSettingsEntity.builder().organizationId("acme").enabled(true).isNew(true).build();

        assertThat(entity.getId()).isEqualTo("acme");
        assertThat(entity.isNew()).isTrue();
    }

    /**
     * A row read back from the database and carried through {@code toBuilder()} must stay an UPDATE. If the flag leaked
     * across that copy, switching an existing organization's roundup off would attempt a second INSERT of its key.
     */
    @Test
    void aRowCarriedForwardFromAReadIsNotNew() {

        var loaded = RoundupSettingsEntity.builder().organizationId("acme").version(3L).build();

        assertThat(loaded.toBuilder().enabled(true).build().isNew()).isFalse();
    }
}
