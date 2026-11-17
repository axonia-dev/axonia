package dev.axonia.identity.api.spi;

/** Authorization seam for the Organization's member name patterns. */
public interface MemberNamesGate {

    /** Throws {@link dev.axonia.shared.api.error.ForbiddenException} if the caller lacks {@code member:read}. */
    void requireRead();

    /** Throws {@link dev.axonia.shared.api.error.ForbiddenException} if the caller lacks {@code member:write}. */
    void requireWrite();
}
