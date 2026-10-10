package dev.axonia.identity.api.spi;

/** Authorization seam for SCIM/directory-sync token administration. */
public interface ScimTokenGate {
    /** Throws {@link dev.axonia.shared.api.error.ForbiddenException} if the caller lacks {@code scim:read}. */
    void requireRead();

    /** Throws {@link dev.axonia.shared.api.error.ForbiddenException} if the caller lacks {@code scim:write}. */
    void requireWrite();
}
