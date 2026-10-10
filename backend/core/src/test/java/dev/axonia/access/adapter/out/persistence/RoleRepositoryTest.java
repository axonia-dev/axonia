package dev.axonia.access.adapter.out.persistence;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class RoleRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    private RoleRepository repository;

    @Test
    void migration_dropsRetiredBuiltinRoles() {
        assertThat(repository.findByOrganizationIdAndName("default", "Taxonomy Manager")).isEmpty();
        assertThat(repository.findByOrganizationIdAndName("default", "Member Manager")).isEmpty();
        assertThat(repository.findByOrganizationIdAndName("default", "Auditor")).isEmpty();
    }

    @Test
    void saveRole_withPermissions_roundTripsViaRepository() {
        // given
        var role = RoleEntityMother.any().name("Test Role " + System.nanoTime())
                .permissions(
                        Set.of(RoleEntityMother.permission("member:read"), RoleEntityMother.permission("member:write")))
                .build();

        // when
        var saved = repository.save(role);
        var loaded = repository.findById(saved.getId());

        // then
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getPermissions()).extracting(RolePermissionEntity::getPermission)
                .containsExactlyInAnyOrder("member:read", "member:write");
    }
}
