package dev.axonia.shared.api.error;

/** Authenticated token carries no org claim and the dev fallback is disabled. Maps to 401. */
public class MissingOrganizationException extends RuntimeException {

    public MissingOrganizationException(String subject) {
        super("Authenticated token for subject " + subject + " carries no org claim");
    }
}
