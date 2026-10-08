package dev.axonia.identity;

import java.time.Duration;

/**
 * Told about a sign-in by just-in-time provisioning, on the first request of each {@link #RESEEN_AFTER} window, so an
 * implementation can write down whatever it keeps about the people who sign in.
 *
 * <p>
 * None of it is what the caller asked for, so an implementation decides for itself which of its failures the request
 * survives. A delegated or agent request tells it nothing: neither is the person signing in.
 */
public interface SignInObserver {

    /**
     * How long provisioning stays quiet about a person once it has told the observers about them. A failure an observer
     * swallows is retried no sooner than this.
     */
    Duration RESEEN_AFTER = Duration.ofMinutes(10);

    /**
     * Told before the caller's profile is upserted, for a token scoped to an Organization. First because the upsert
     * rewrites {@code identity_user.email} to the address the member has just arrived with.
     */
    void beforeProvisioning(IdentityClaims claims);

    /** Told after the caller's profile is upserted, for a token scoped to an Organization. */
    void afterProvisioning(IdentityClaims claims);

    /**
     * Told for every signed-in person, whether or not the token names an Organization, after any provisioning, in a
     * window of its own kept per Organization and subject.
     */
    void signedIn(IdentityClaims claims);
}
