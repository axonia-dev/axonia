package dev.axonia.identity;

/**
 * Whether some role binding is already waiting on a directory group key, asked by SCIM provisioning before it accepts a
 * group under that key.
 *
 * <p>
 * A SCIM client chooses its own {@code externalId} and a {@code GROUP} role binding matches on exactly that value, so
 * without this question a provisioning credential is a way into whatever authority somebody had already bound to the
 * key. The answer does not refuse the write - binding a role to a group before the directory pushes it is the ordinary
 * enterprise onboarding order - it only decides whether the group arrives confirmed or held.
 *
 * <p>
 * Declared here, in {@code identity}, and implemented in {@code access}, because {@code access} already depends on
 * {@code identity} through {@link UserDirectory} and the reverse edge would be a cycle. The port stays deliberately
 * narrow for the same reason: identity asks one yes-or-no question and learns nothing about roles, permissions or
 * bindings.
 */
public interface DirectoryGroupClaims {

    /** Whether a role binding in this Organization already names {@code reconciliationKey} as its group subject. */
    boolean isClaimedByARoleBinding(String organizationId, String reconciliationKey);
}
