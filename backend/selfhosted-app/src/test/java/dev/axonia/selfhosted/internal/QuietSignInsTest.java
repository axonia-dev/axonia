package dev.axonia.selfhosted.internal;

import org.junit.jupiter.api.Test;

import dev.axonia.identity.IdentityClaims;

import static org.assertj.core.api.Assertions.assertThatNoException;

class QuietSignInsTest {

    private final QuietSignIns observer = new QuietSignIns();

    @Test
    void observesEveryStepOfASignInWithoutActing() {

        // given
        var claims = IdentityClaims.builder().userId("sub-1").organizationId("acme").build();

        // when / then
        assertThatNoException().isThrownBy(() -> {
            observer.beforeProvisioning(claims);
            observer.afterProvisioning(claims);
            observer.signedIn(claims);
        });
    }
}
