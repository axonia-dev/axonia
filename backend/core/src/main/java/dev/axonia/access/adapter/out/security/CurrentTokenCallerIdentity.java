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
 * Reads the caller's identity from the JWT, resolving its {@code groups} claim to group keys through
 * {@link DirectoryGroups#groupKeysFor}, which decides when the SCIM-synced directory stands in for the claim or adds to
 * it.
 *
 * <p>
 * The authorization gate re-derives the caller's groups on every permission check, several times per request. The
 * resolved keys are therefore memoized in the current request's attributes, never in a field: this component is a
 * singleton shared by every concurrent request, so a field would serve one caller's groups to another. The cached entry
 * additionally carries the subject, Organization and claim it was resolved for and is ignored unless all match, so a
 * request that changes caller mid-flight re-resolves rather than inherits.
 *
 * <p>
 * The directory is never consulted for an agent token ({@link CurrentToken#isAgentToken()}). An agent holds only the
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
        return resolve(organizationId(), subject(), CurrentToken.groups());
    }

    private List<String> resolve(String organizationId, String userId, List<String> claimed) {

        var request = RequestContextHolder.getRequestAttributes();
        if (request == null) {
            return directoryGroups.groupKeysFor(organizationId, userId, claimed);
        }
        if (cached(request) instanceof ResolvedGroups hit && hit.resolvedFor(organizationId, userId, claimed)) {
            return hit.keys();
        }
        var resolved = directoryGroups.groupKeysFor(organizationId, userId, claimed);
        request.setAttribute(CACHE_KEY, new ResolvedGroups(organizationId, userId, claimed, resolved),
                RequestAttributes.SCOPE_REQUEST);
        return resolved;
    }

    private static Object cached(RequestAttributes request) {

        return request.getAttribute(CACHE_KEY, RequestAttributes.SCOPE_REQUEST);
    }

    private record ResolvedGroups(String organizationId, String userId, List<String> claimed, List<String> keys) {

        boolean resolvedFor(String organizationId, String userId, List<String> claimed) {

            return Objects.equals(this.organizationId, organizationId) && Objects.equals(this.userId, userId)
                    && this.claimed.equals(claimed);
        }
    }
}
