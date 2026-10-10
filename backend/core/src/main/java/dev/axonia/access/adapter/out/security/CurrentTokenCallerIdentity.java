package dev.axonia.access.adapter.out.security;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import dev.axonia.access.application.port.out.CallerIdentity;
import dev.axonia.identity.api.DirectoryGroups;
import dev.axonia.shared.api.security.CurrentToken;
import lombok.RequiredArgsConstructor;

/**
 * Reads the caller's identity from the JWT, supplementing the {@code groups} claim from the SCIM-synced directory when
 * the claim is empty and the Organization's identity provider can drop it.
 *
 * <p>
 * Entra's group-claim overage markers ({@code _claim_names}/{@code _claim_sources}) are not read here: a
 * Keycloak-brokered import drops them, and a token Entra issues directly carries them in place of the claim. Either way
 * the token arrives without groups, so overage is undetectable here, and an empty claim is ambiguous between "no
 * groups", "overage", and "misconfigured mapper".
 *
 * <p>
 * The claim stays the primary source and is always current. The directory is consulted only when the claim is empty,
 * which confines its staleness (as fresh as the last SCIM push, up to roughly 40 minutes) to the case that is otherwise
 * a silent denial, and only for the Entra-connected Organizations where overage is possible at all. The read is a
 * single local indexed join over already-synced tables, so it does not violate {@code IdentityProviderDirectory}'s
 * prohibition on outbound calls from the authorisation path.
 *
 * <p>
 * The authorization gate re-derives the caller's groups on every permission check, several times per request. The
 * resolved keys are therefore memoized in the current request's attributes, never in a field: this component is a
 * singleton shared by every concurrent request, so a field would serve one caller's groups to another. The cached entry
 * additionally carries the subject and Organization it was resolved for and is ignored unless both match, so a request
 * that changes caller mid-flight re-resolves rather than inherits.
 *
 * <p>
 * The substitution never runs for an agent token ({@link CurrentToken#isAgentToken()}). An agent holds only the
 * permissions bound to the person who minted its token, never a group's: a SCIM client chooses its own group keys, so
 * letting the directory speak for an agent would turn "may provision" into "may be Owner".
 */
@Component
@RequiredArgsConstructor
class CurrentTokenCallerIdentity implements CallerIdentity {

    private static final String CACHE_KEY = CurrentTokenCallerIdentity.class.getName() + ".resolvedGroups";

    private final DirectoryGroups directoryGroups;

    @Override
    public String subject() {
        return CurrentToken.subject();
    }

    @Override
    public String organizationId() {
        return CurrentToken.organization();
    }

    @Override
    public boolean isAgent() {
        return CurrentToken.isAgentToken();
    }

    @Override
    public List<String> groups() {

        if (isAgent()) {
            // an agent holds the permissions bound to the person who minted its token and nothing a group
            // confers. The SCIM client chooses its own group keys, so a directory group speaking for an agent would
            // turn "may provision" into "may be Owner".
            return List.of();
        }
        var claimed = CurrentToken.groups();
        if (!claimed.isEmpty()) {
            return claimed;
        }
        return substituteForAbsentClaim(organizationId(), subject());
    }

    private List<String> substituteForAbsentClaim(String organizationId, String userId) {

        var request = RequestContextHolder.getRequestAttributes();
        if (request == null) {
            return directoryGroups.reconciliationKeysSubstitutingForAbsentClaim(organizationId, userId);
        }
        if (cached(request) instanceof ResolvedGroups hit && hit.resolvedFor(organizationId, userId)) {
            return hit.keys();
        }
        var resolved = directoryGroups.reconciliationKeysSubstitutingForAbsentClaim(organizationId, userId);
        request.setAttribute(CACHE_KEY, new ResolvedGroups(organizationId, userId, resolved),
                RequestAttributes.SCOPE_REQUEST);
        return resolved;
    }

    private static Object cached(RequestAttributes request) {

        return request.getAttribute(CACHE_KEY, RequestAttributes.SCOPE_REQUEST);
    }

    private record ResolvedGroups(String organizationId, String userId, List<String> keys) {

        boolean resolvedFor(String organizationId, String userId) {

            return Objects.equals(this.organizationId, organizationId) && Objects.equals(this.userId, userId);
        }
    }
}
