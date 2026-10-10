package dev.axonia.selfhosted.domain;

import lombok.experimental.UtilityClass;

/**
 * Every account, role binding and membership is keyed on the value of the subject claim the installation was founded
 * with. Another claim names every person differently, so each would arrive as a stranger while their history stayed
 * with an account nobody can sign in to: the start is refused instead.
 */
@UtilityClass
public class SubjectClaimLock {

    public void verify(String recorded, String configured) {

        if (!recorded.equals(configured)) {
            throw new IllegalStateException(("The installation was founded with the subject claim '%s', and "
                    + "axonia.selfhosted.oidc.subject-claim now names '%s'. Every account is matched on that claim, "
                    + "so set it back to '%s'.").formatted(recorded, configured, recorded));
        }
    }
}
