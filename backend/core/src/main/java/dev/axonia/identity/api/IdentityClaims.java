package dev.axonia.identity.api;

import lombok.Builder;

/**
 * The identity-relevant claims of the caller's token, already normalised (display name resolved, organization resolved
 * from the token when present, {@code null} otherwise). {@code organizationId} is {@code null} when the token carries
 * no {@code org} claim at all (e.g. a fresh social sign-in before any per-org profile exists); callers on org-scoped
 * paths never see a null here.
 *
 * <p>
 * {@code sid} is the token's own session id, {@code null} when the token carries none - a personal access token or a
 * synthesized delegated session never has one. It exists only so that a list of a member's own signed-in sessions can
 * mark which row is the one the request is asking through; nothing else in the identity core reads it.
 */
@Builder
public record IdentityClaims(String userId, String organizationId, String displayName, String email, String avatarUrl,
        String sid) {
}
