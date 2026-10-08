package dev.axonia;

import java.util.List;
import java.util.Optional;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import lombok.extern.slf4j.Slf4j;

/**
 * Shared-kernel reader of the caller's identity, pulled off the JWT that Spring Security placed in the SecurityContext.
 * The per-context CurrentUser/OrganizationContext adapters delegate here so claim handling exists exactly once; the
 * port interfaces stay per-context and the module graph is unchanged.
 */
@Slf4j
public final class CurrentToken {

    static final String ORGANIZATION_CLAIM = "org";
    static final String GROUPS_CLAIM = "groups";
    static final String DEFAULT_ORGANIZATION = "default";
    private static final String TOKEN_ID_CLAIM = "tokenId";
    private static final String CONNECTION_ID_CLAIM = "connectionId";

    /** RFC 8693's actor claim: present when somebody other than the person acts through this token. */
    public static final String ACTOR_CLAIM = "act";

    private static volatile boolean allowDefaultFallback = false;

    public static void setAllowDefaultFallback(boolean allow) {
        allowDefaultFallback = allow;
    }

    private CurrentToken() {
    }

    /** The JWT {@code sub} — the stable user id everywhere a user id is stored. */
    public static String subject() {

        return jwt().getSubject();
    }

    /**
     * The custom {@code org} claim — the active Organization. When the claim is absent or blank: if the dev fallback is
     * enabled (flag set via {@link #setAllowDefaultFallback} or
     * {@code axonia.organization.allow-default-fallback=true}), logs a warning and returns {@code "default"}; otherwise
     * throws {@link MissingOrganizationException} (→ 401 in prod).
     */
    public static String organization() {

        return resolvedOrganization().orElseThrow(() -> new MissingOrganizationException(subject()));
    }

    /**
     * Non-throwing counterpart to {@link #organization()}: the {@code org} claim when present & non-blank; else, if the
     * dev fallback is enabled, logs the existing warning and resolves to {@code "default"}; otherwise
     * {@link Optional#empty()} — lets callers that must not 401 on an org-less token (e.g. the shared-kernel
     * {@code OrganizationResolver}, which fails RLS closed on empty rather than throwing) tell "no active Organization"
     * apart from a hard failure.
     */
    public static Optional<String> resolvedOrganization() {

        var org = jwt().getClaimAsString(ORGANIZATION_CLAIM);
        if (org == null || org.isBlank()) {
            if (allowDefaultFallback) {
                log.warn("JWT for subject {} carries no org claim; defaulting to '{}'", subject(),
                        DEFAULT_ORGANIZATION);
                return Optional.of(DEFAULT_ORGANIZATION);
            }
            return Optional.empty();
        }
        return Optional.of(org);
    }

    /**
     * Whether the caller's token carries an active {@code org} claim, without throwing when it is absent. Lets
     * Organization-agnostic paths (e.g. {@code GET /me/organizations}, which lists the Organizations a not-yet-scoped
     * user may switch into) and JIT provisioning tell "no active Organization" apart from a hard failure, instead of
     * tripping the fail-closed {@link #organization()} throw.
     */
    public static boolean hasOrganization() {

        var org = jwt().getClaimAsString(ORGANIZATION_CLAIM);
        return org != null && !org.isBlank();
    }

    /**
     * The JWT {@code groups} claim — group names the caller belongs to; empty list when absent.
     *
     * <p>
     * The {@code groups} claim carries group <em>names</em>, which is what the auth gate compares against the stored
     * {@code subject_id}.
     *
     * <p>
     * <strong>Entra claim overage:</strong> when a member's group count exceeds Entra's token limit the token carries
     * no groups, and this returns an empty list. Overage cannot be detected here: this reads only the claim, never
     * Entra's {@code _claim_names} markers, which a Keycloak-brokered import drops anyway. The supplement lives in
     * {@code CurrentTokenCallerIdentity}, which falls back to the SCIM-synced directory when this claim is empty.
     */
    public static List<String> groups() {

        var groups = jwt().getClaimAsStringList(GROUPS_CLAIM);
        return groups == null ? List.of() : groups;
    }

    /** Any other claim as a string, or {@code null} when absent. */
    public static String claim(String name) {

        return jwt().getClaimAsString(name);
    }

    /**
     * Whether this request is an agent's rather than a person's browser session.
     *
     * <p>
     * Neither agent credential comes from the identity provider: {@code AgentAuthenticationFilter} synthesizes an
     * in-process JWT for a personal access token or an MCP connection token, and the {@code tokenId} or
     * {@code connectionId} claim it carries is the one thing no token from the identity provider does. Either claim
     * marks an agent call. Reading it here rather than in the filter keeps the question answerable from anywhere the
     * security context reaches, which is where authorization decisions are actually made.
     */
    public static boolean isAgentToken() {

        return jwt().getClaimAsString(TOKEN_ID_CLAIM) != null || jwt().getClaimAsString(CONNECTION_ID_CLAIM) != null;
    }

    /**
     * Whether somebody other than the person acts through this request, rather than the person's own sign-in. Only a
     * token an edition mints for such a session carries {@link #ACTOR_CLAIM}, and the resource server refuses a token
     * that arrives from the identity provider with one, so its presence is the whole test.
     */
    public static boolean isDelegated() {

        return jwt().hasClaim(ACTOR_CLAIM);
    }

    private static Jwt jwt() {

        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        throw new IllegalStateException("No authenticated JWT in the security context");
    }
}
