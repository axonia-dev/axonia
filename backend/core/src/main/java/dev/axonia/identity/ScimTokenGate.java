package dev.axonia.identity;

/** Authorization seam for SCIM/directory-sync token administration. */
public interface ScimTokenGate {
    /** Throws {@link dev.axonia.ForbiddenException} if the caller lacks {@code scim:read}. */
    void requireRead();

    /** Throws {@link dev.axonia.ForbiddenException} if the caller lacks {@code scim:write}. */
    void requireWrite();
}
