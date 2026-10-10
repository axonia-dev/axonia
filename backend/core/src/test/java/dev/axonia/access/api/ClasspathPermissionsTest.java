package dev.axonia.access.api;

import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.axonia.access.api.spi.ClasspathPermissions;
import dev.axonia.access.api.spi.PermissionContribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClasspathPermissionsTest {

    @Test
    void theOpenCoreContributesNoPermissionOfItsOwn() {

        assertThat(ClasspathPermissions.contributions()).isEmpty();
        assertThat(ClasspathPermissions.known()).isEqualTo(Permission.OPEN);
    }

    @Test
    void refusesAContributionTheApplicationWouldNeverWire() {

        assertThatThrownBy(() -> ClasspathPermissions.instantiate(Unwired.class))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not a Spring component");
    }

    /** Never a {@code @Component}: core's Spring contexts scan test classes, and would wire it. */
    static class Unwired implements PermissionContribution {

        @Override
        public Set<Permission> permissions() {
            return Set.of();
        }
    }
}
