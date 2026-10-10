package dev.axonia.access.application;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.axonia.access.api.Permission;
import dev.axonia.access.api.spi.PermissionCatalogue;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicablePermissionsTest {

    private static final Permission AUDIT_READ = new Permission("audit:read");

    private final KnownPermissions known = new KnownPermissions(List.of(() -> Set.of(AUDIT_READ)));

    @Test
    void withNoCatalogueEveryKnownPermissionApplies() {

        var sut = new ApplicablePermissions(List.of(), known);

        assertThat(sut.in("acme")).isEqualTo(known.all()).contains(AUDIT_READ);
    }

    @Test
    void aCatalogueWithNothingToSayLeavesTheEnterpriseAnswer() {

        PermissionCatalogue silent = organizationId -> Optional.empty();
        var sut = new ApplicablePermissions(List.of(silent), known);

        assertThat(sut.in("acme")).isEqualTo(known.all());
    }

    @Test
    void aCatalogueThatAnswersForTheOrganizationDecides() {

        PermissionCatalogue narrow = organizationId -> "rust-hub".equals(organizationId)
                ? Optional.of(Set.of(Permission.MEMBER_READ))
                : Optional.empty();
        var sut = new ApplicablePermissions(List.of(narrow), known);

        assertThat(sut.in("rust-hub")).containsExactly(Permission.MEMBER_READ);
        assertThat(sut.in("acme")).isEqualTo(known.all());
    }
}
