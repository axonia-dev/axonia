package dev.axonia.selfhosted.internal;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class DirectoryOwnedAccountsTest {

    @Test
    void deletingAnAccountLeavesTheCompanyDirectoryAlone() {

        // given
        var sut = new DirectoryOwnedAccounts();

        // when / then
        assertThatNoException().isThrownBy(() -> sut.deleteAccount("sub-1"));
    }

    /** Nothing to call out with: no collaborator can be injected, and none is held. */
    @Test
    void holdsNoCollaboratorThatCouldReachAnIdentityProvider() {

        // when
        var actualConstructors = DirectoryOwnedAccounts.class.getDeclaredConstructors();
        var actualFields = Arrays.stream(DirectoryOwnedAccounts.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic());

        // then
        assertThat(actualConstructors).isNotEmpty()
                .allSatisfy(constructor -> assertThat(constructor.getParameterCount()).isZero());
        assertThat(actualFields).extracting(Field::getName).isEmpty();
    }
}
