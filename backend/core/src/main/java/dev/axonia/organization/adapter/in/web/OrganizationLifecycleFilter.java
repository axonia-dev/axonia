package dev.axonia.organization.adapter.in.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import dev.axonia.organization.api.Organization;
import dev.axonia.organization.application.OrganizationService;
import dev.axonia.shared.api.tenancy.OrganizationResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * The off switch. An Organization that is not {@code ACTIVE} serves no requests, on any surface, through any
 * credential.
 *
 * <p>
 * It sits on {@link OrganizationResolver}, the one seam every authenticated request already crosses (it is what sets
 * the {@code app.current_org} GUC for row-level security), so a single check covers every credential path: a JWT from
 * the identity provider, a bearer a module contributes, an agent personal access token, and a SCIM directory-sync
 * token. Enforcing at the identity provider instead would leave the others untouched and let an already-issued token
 * run out its full lifetime.
 *
 * <p>
 * An empty resolution passes through. That covers anonymous callers, a token carrying no {@code org} claim, and a call
 * a module contributes outside an Organization, whose authentication is neither a JWT nor Organization-bound. Such a
 * surface must stay open while an Organization is suspended, because unsuspending it may be a call to that surface.
 *
 * <p>
 * The {@code /public/**} surface is exempt by path rather than by resolution, and that distinction is the whole point.
 * Those endpoints are authorized by what the caller holds - an invite code, a hub's public address - never by the
 * Organization they happen to be standing in, and an anonymous caller already reaches every one of them. A signed-in
 * member must therefore never get <em>less</em> than a stranger would: their bearer rides along on these routes (the
 * app publishes it so a join or an accept carries their identity), which authenticates them, which resolves their
 * Organization, which without this would have the gate turn an invitation to a perfectly healthy Community Hub into
 * "this invite link is invalid or has expired". Suspending one account must not confiscate an invitation to another.
 *
 * <p>
 * The SCIM error schema is spelled out here rather than imported from
 * {@code dev.axonia.identity.adapter.in.scim.ScimError}: {@code identity} already depends on {@code organization}, so
 * the import would close a module cycle and fail {@code ModularityTests}.
 */
@Slf4j
@RequiredArgsConstructor
public class OrganizationLifecycleFilter extends OncePerRequestFilter {

    private static final String SCIM_PATH_PREFIX = "/scim/v2/";
    private static final String PUBLIC_PATH_PREFIX = "/public/";
    private static final String SCIM_JSON = "application/scim+json";
    private static final List<String> SCIM_ERROR_SCHEMAS = List.of("urn:ietf:params:scim:api:messages:2.0:Error");

    private static final String UNKNOWN_DETAIL = "Unknown Organization.";
    private static final String SUSPENDED_DETAIL = "This Organization is suspended.";
    private static final String CLOSED_DETAIL = "This Organization is closed.";

    private final OrganizationResolver resolver;
    private final OrganizationService organizations;
    private final JsonMapper jsonMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(PUBLIC_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        var organizationId = resolver.currentOrganization();
        if (organizationId.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        var organization = organizations.organization(organizationId.get());
        if (organization.isEmpty()) {
            log.warn("Rejected a request naming an unknown Organization: {}", organizationId.get());
            writeRefusal(request, response, HttpStatus.UNAUTHORIZED,
                    new Refusal("unknown_organization", UNKNOWN_DETAIL));
            return;
        }

        var refusal = refusalFor(organization.get());
        if (refusal.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        writeRefusal(request, response, HttpStatus.FORBIDDEN, refusal.get());
    }

    private static Optional<Refusal> refusalFor(Organization organization) {

        return switch (organization.lifecycleState()) {
            case ACTIVE -> Optional.empty();
            case SUSPENDED -> Optional.of(new Refusal("organization_suspended", SUSPENDED_DETAIL));
            case CLOSED -> Optional.of(new Refusal("organization_closed", CLOSED_DETAIL));
        };
    }

    private void writeRefusal(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
            Refusal refusal) throws IOException {

        response.setStatus(status.value());
        if (request.getRequestURI().startsWith(SCIM_PATH_PREFIX)) {
            response.setContentType(SCIM_JSON);
            response.getWriter().write(jsonMapper.writeValueAsString(Map.of("schemas", SCIM_ERROR_SCHEMAS, "status",
                    String.valueOf(status.value()), "detail", refusal.detail(), "code", refusal.code())));
            return;
        }
        var problem = ProblemDetail.forStatusAndDetail(status, refusal.detail());
        problem.setProperty("code", refusal.code());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(jsonMapper.writeValueAsString(problem));
    }

    private record Refusal(String code, String detail) {
    }
}
