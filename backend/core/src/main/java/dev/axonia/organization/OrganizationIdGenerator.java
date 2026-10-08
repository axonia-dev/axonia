package dev.axonia.organization;

/** Mints opaque tenant identifiers. Kept a port so tests can supply a deterministic id. */
public interface OrganizationIdGenerator {

    /** A fresh, globally-unique, URL-safe organization id (lowercase 26-char Crockford base32 ULID). */
    String newId();
}
