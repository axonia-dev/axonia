package dev.axonia.access.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAssignMigrationTest extends CoreJdbcTestSupport {

    @Autowired
    private RoleRepository repository;

    @Test
    void staleRoles_areGone() {
        assertThat(repository.findByOrganizationIdAndName("default", "Tenant Owner")).isEmpty();
        assertThat(repository.findByOrganizationIdAndName("default", "Organization Admin")).isEmpty();
        assertThat(repository.findByOrganizationIdAndName("default", "User")).isEmpty();
        assertThat(repository.findByOrganizationIdAndName("default", "Member")).isEmpty();
    }
}
