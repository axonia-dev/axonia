package dev.axonia.selfhosted.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectClaimLockTest {

    @Test
    void acceptsTheClaimTheInstallationWasFoundedWith() {

        assertThatNoException().isThrownBy(() -> SubjectClaimLock.verify("oid", "oid"));
    }

    @Test
    void refusesAnotherClaimAndNamesBoth() {

        assertThatThrownBy(() -> SubjectClaimLock.verify("sub", "oid")).isInstanceOf(IllegalStateException.class)
                .hasMessage("The installation was founded with the subject claim 'sub', and "
                        + "axonia.selfhosted.oidc.subject-claim now names 'oid'. Every account is matched on that "
                        + "claim, so set it back to 'sub'.");
    }
}
