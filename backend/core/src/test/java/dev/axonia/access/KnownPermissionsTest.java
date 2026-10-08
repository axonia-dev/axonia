package dev.axonia.access;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnownPermissionsTest {

    private static final Permission AUDIT_READ = new Permission("audit:read");

    @Test
    void withNoContributionTheEditionKnowsTheOpenPermissions() {

        assertThat(new KnownPermissions(List.of()).all()).isEqualTo(Permission.OPEN);
    }

    @Test
    void aContributedPermissionIsKnownBesideTheOpenOnes() {

        PermissionContribution audit = () -> Set.of(AUDIT_READ);

        var sut = new KnownPermissions(List.of(audit));

        assertThat(sut.all()).containsAll(Permission.OPEN).contains(AUDIT_READ).hasSize(Permission.OPEN.size() + 1);
        assertThat(sut.byCode("audit:read")).contains(AUDIT_READ);
    }

    @Test
    void aCodeNothingDeclaresIsUnknown() {

        assertThat(new KnownPermissions(List.of()).byCode("audit:read")).isEmpty();
    }

    @Test
    void refusesAPermissionDeclaredTwice() {

        PermissionContribution again = () -> Set.of(Permission.ROLE_WRITE);

        assertThatThrownBy(() -> new KnownPermissions(List.of(again))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("role:write is declared twice");
    }
}
