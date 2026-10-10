package dev.axonia.privacy.domain;

import java.util.UUID;

/**
 * The identity an erased person's surviving rows carry. One surrogate is minted per erasure per Organization: every
 * one-row-per-user constraint still holds, distinct-contributor counts and bus factor stay truthful, and no mapping
 * back to the person is kept anywhere, so it cannot be resolved. Scoping it per Organization also means traces left in
 * two different hubs cannot be tied to each other.
 *
 * <p>
 * The {@code erased:} prefix is what makes a surrogate recognisable as one and impossible to confuse with an identity
 * provider subject, which is a bare UUID.
 */
public final class SurrogateIds {

    private static final String PREFIX = "erased:";

    private SurrogateIds() {
    }

    public static String mint() {

        return PREFIX + UUID.randomUUID();
    }
}
