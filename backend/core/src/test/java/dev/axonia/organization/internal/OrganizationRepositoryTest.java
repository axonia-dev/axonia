package dev.axonia.organization.internal;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.organization.OrganizationKind;
import dev.axonia.organization.mother.OrganizationEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    private OrganizationRepository repository;

    @Test
    void save_insertsNewOrganization() {

        // given
        var org = OrganizationEntity.builder().id("acme").name("Acme Corp").kind(OrganizationKind.ENTERPRISE)
                .isNew(true).build();

        // when
        var actual = repository.save(org);

        // then
        assertThat(actual.getId()).isEqualTo("acme");
        assertThat(actual.getName()).isEqualTo("Acme Corp");
        assertThat(actual.getCreatedAt()).isNotNull();
        assertThat(actual.getUpdatedAt()).isNotNull();
    }

    @Test
    void save_duplicateId_violatesPrimaryKeyConstraint() {

        // given
        repository.save(OrganizationEntityMother.any().isNew(true).build());

        // when / then
        assertThatThrownBy(() -> repository.save(OrganizationEntityMother.any().isNew(true).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findById_unknownId_returnsEmpty() {

        // when
        var actual = repository.findById("nonexistent-tenant");

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void updateCommunicationsEnabled_flipsTheFlagAndStampsUpdatedAt() {

        // given: ships on by default
        repository.save(OrganizationEntityMother.any().id("hub-e").kind(OrganizationKind.HUB).isNew(true).build());
        var stampedAt = Instant.parse("2026-08-01T09:30:00Z");

        // when
        var affected = repository.updateCommunicationsEnabled("hub-e", false, stampedAt);

        // then: the write actually reaches the database under the exact column names V24 declares, and updated_at is
        // stamped from the caller's clock rather than left stale by the raw UPDATE
        assertThat(affected).isEqualTo(1);
        var reloaded = repository.findById("hub-e").orElseThrow();
        assertThat(reloaded.isCommunicationsEnabled()).isFalse();
        assertThat(reloaded.getUpdatedAt()).isEqualTo(stampedAt);
    }

    @Test
    void updateCommunicationsEnabled_unknownId_affectsNoRows() {

        // when
        var affected = repository.updateCommunicationsEnabled("ghost", false, Instant.now());

        // then: no row exists to update; the caller uses this to fail loudly rather than silently no-op
        assertThat(affected).isZero();
    }
}
