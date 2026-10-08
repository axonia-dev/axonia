package dev.axonia.organization;

/**
 * Inbound port for the {@code organization:write} permission check.
 *
 * <p>
 * Defined here (in the organization module) rather than importing {@code dev.axonia.access.AuthorizationGate} directly,
 * so the organization module does not take a compile-time dependency on the access module. The access module depends on
 * the organization module (it implements this port), so a reverse dependency would create a cycle that the Spring
 * Modulith {@code ModularityTests} would reject.
 *
 * <p>
 * The adapter lives in {@code access.internal} ({@code OrganizationWriteGateAdapter}) where the access module is free
 * to reference the organization port without introducing a new inter-module edge.
 */
public interface OrganizationWriteGate {

    /** Throws {@link dev.axonia.ForbiddenException} if the caller lacks {@code organization:write}. */
    void requireWrite();

    /**
     * Throws {@link dev.axonia.ForbiddenException} if the caller lacks {@code communication:write}.
     *
     * <p>
     * Separate from {@link #requireWrite()} because silencing every community's mail across a tenant is not the same
     * authority as renaming the account, and it used to be: the kill switch rode on the generic organization write
     * right, so anyone who could edit the workspace profile could also stop every message it sends.
     */
    void requireCommunicationWrite();
}
