package dev.axonia.organization.adapter.out.persistence;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.organization.api.OrganizationKind;
import dev.axonia.organization.api.OrganizationLifecycleState;
import dev.axonia.shared.api.tenancy.OrganizationScope;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationLifecyclePersistenceTest extends CoreJdbcTestSupport {

    @Autowired
    OrganizationRepository repository;

    @Test
    void newEnterpriseOrgDefaultsToActive() {
        OrganizationScope.runAs("acme-1", () -> repository.save(OrganizationEntity.builder().id("acme-1").name("Acme")
                .kind(OrganizationKind.ENTERPRISE).isNew(true).build()));

        var found = OrganizationScope.runAs("acme-1", () -> repository.findById("acme-1")).orElseThrow();
        assertThat(found.getLifecycleState()).isEqualTo(OrganizationLifecycleState.ACTIVE);
        assertThat(found.getClosedAt()).isNull();
    }

    @Test
    void closedStampsPersist() {
        var stamp = Instant.parse("2026-07-24T10:00:00Z");
        OrganizationScope.runAs("acme-2",
                () -> repository.save(OrganizationEntity.builder().id("acme-2").name("Acme2")
                        .kind(OrganizationKind.ENTERPRISE).lifecycleState(OrganizationLifecycleState.CLOSED)
                        .closedAt(stamp).closedBy("op-1").isNew(true).build()));

        var found = OrganizationScope.runAs("acme-2", () -> repository.findById("acme-2")).orElseThrow();
        assertThat(found.getLifecycleState()).isEqualTo(OrganizationLifecycleState.CLOSED);
        assertThat(found.getClosedBy()).isEqualTo("op-1");
        assertThat(found.getClosedAt()).isEqualTo(stamp);
    }
}
