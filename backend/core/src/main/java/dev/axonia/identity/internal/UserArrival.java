package dev.axonia.identity.internal;

/**
 * How an {@code identity_user} row came to exist. Stamped once at insert and never reassigned: how a person arrived
 * cannot change on a later sign-in or SCIM update. Persisted as-is in {@code identity_user.arrival}, which another
 * module reads directly by column name, so it is part of the table's contract.
 */
public enum UserArrival {

    /** Created by the just-in-time sign-in path, with no SCIM push involved. */
    FIRST_SIGN_IN,

    /** Created by a SCIM provisioning call, ahead of any sign-in. */
    SYNCED
}
