package dev.axonia.identity.domain;

/**
 * A member's name as axonia shows it: the tidied {@code displayName}, and the {@code givenName} a declared name pattern
 * read out of it, null when no pattern matched.
 */
public record ReadName(String displayName, String givenName) {
}
