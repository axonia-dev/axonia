package dev.axonia.identity;

import java.util.Set;

/**
 * The release valve for a directory group that SCIM provisioning held because a role binding was already waiting on its
 * key (see {@link DirectoryGroupClaims}).
 *
 * <p>
 * A held group confers nothing: {@link DirectoryGroups#reconciliationKeysForUser} skips it, so no role bound to that
 * key reaches its members. Confirmation is the moment an administrator says the group the directory pushed really is
 * the one they meant, and it is deliberately the only way out of that state - which is why {@code access} gates it
 * behind {@code role:assign}, the permission a SCIM writer is precisely the one not to hold.
 */
public interface DirectoryGroupConfirmation {

    /** The reconciliation keys of this Organization's synced groups that are still held unconfirmed. */
    Set<String> keysAwaitingConfirmation(String organizationId);

    /**
     * Confirms the synced group reconciled by {@code reconciliationKey}, stamping the confirmation time from the
     * application's clock. Idempotent: a group that is already confirmed keeps its original stamp and still answers
     * true. False means no synced group carries that key at all, which is the ordinary state of a binding authored
     * before the directory ever pushed the group.
     */
    boolean confirm(String organizationId, String reconciliationKey);
}
