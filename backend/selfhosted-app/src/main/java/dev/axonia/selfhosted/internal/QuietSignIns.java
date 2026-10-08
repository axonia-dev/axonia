package dev.axonia.selfhosted.internal;

import org.springframework.stereotype.Component;

import dev.axonia.identity.IdentityClaims;
import dev.axonia.identity.SignInObserver;

/** A sign-in to a single-company installation has nothing beyond the profile itself to record. */
@Component
class QuietSignIns implements SignInObserver {

    @Override
    public void beforeProvisioning(IdentityClaims claims) {
        // no platform account to reconcile before the profile exists
    }

    @Override
    public void afterProvisioning(IdentityClaims claims) {
        // no platform account to link to the new profile
    }

    @Override
    public void signedIn(IdentityClaims claims) {
        // no sign-in history beyond the profile's own
    }
}
