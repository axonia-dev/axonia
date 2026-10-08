package dev.axonia.identity;

/**
 * The account a person signs in with, at the identity provider that holds it.
 *
 * <p>
 * Asked by self-serve account erasure alone, as its last step: after every Organization's data has been erased, and
 * outside every erasure transaction. An edition whose accounts live in the company's own directory deletes nothing,
 * because that directory is not axonia's to write.
 */
public interface IdentityAccounts {

    /**
     * Deletes the account {@code subject} signs in with. Idempotent: an account that is already absent (a re-run after
     * a prior partial failure, where this step already succeeded) is success, not an error.
     */
    void deleteAccount(String subject);
}
