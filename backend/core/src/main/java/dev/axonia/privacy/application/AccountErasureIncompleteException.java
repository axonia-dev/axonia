package dev.axonia.privacy.application;

/**
 * The database half of a self-serve account erasure committed across every Organization the person belonged to, but the
 * final, identity-provider deletion failed. Thrown instead of letting the raw identity provider failure propagate bare
 * - reporting a blanket failure after the data is genuinely gone would tell the person their erasure failed and to try
 * again from scratch, when what actually remains is one already-safe cleanup step. Retrying is safe by construction: an
 * already-erased Organization has no membership left to find, and {@code IdentityAccounts#deleteAccount} is idempotent
 * on 404.
 */
public class AccountErasureIncompleteException extends RuntimeException {

    public AccountErasureIncompleteException(String subject, int organizationsErased, Throwable cause) {

        super(("Account data for '%s' was erased across %d Organization(s); the identity-provider account deletion "
                + "failed and must be retried to finish signing them out everywhere")
                .formatted(subject, organizationsErased), cause);
    }
}
