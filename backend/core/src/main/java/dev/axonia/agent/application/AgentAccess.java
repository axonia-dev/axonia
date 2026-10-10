package dev.axonia.agent.application;

/**
 * Whether an organization or a person may connect agents at all: the one place the commercial rule behind the MCP
 * surface lives.
 *
 * <p>
 * The rule is the paid axis's: see {@link dev.axonia.shared.api.paid.PaidCapability}. When it changes, the single
 * implementation of this interface changes and no caller moves.
 */
public interface AgentAccess {

    /**
     * Whether the given organization, or the given person within it, may mint agent credentials and call the MCP
     * surface.
     */
    boolean isAvailableTo(String organizationId, String subject);

    /**
     * Whether the caller of the current request may. A member acting in no organization at all may not: there is no
     * organization to carry the capability, so the answer is a definitive no rather than an error.
     *
     * <p>
     * Separate from {@link #isAvailableTo(String, String)} because reading the caller's organization is exactly where
     * this got it wrong once: {@code CurrentToken.organization()} substitutes the {@code default} organization for an
     * absent {@code org} claim wherever the local-development fallback is enabled, which silently handed a brand-new
     * member belonging to nothing the capability of whichever kind that organization happens to be.
     */
    boolean isAvailableToCaller();

    /** What a caller reads when connecting an agent is refused to them. */
    String refusal();
}
