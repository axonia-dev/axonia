package dev.axonia.identity;

/**
 * Port: which identity provider vouched for the caller, as a label worth showing. Derived per request from the token
 * and never stored: it is a fact about this sign-in, not about the person.
 */
public interface SignInProviderResolver {

    /** The label, or null when the sign-in names no provider worth showing. */
    String resolve(String organizationId);
}
