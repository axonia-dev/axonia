package dev.axonia.identity.adapter.in.web;

import java.io.IOException;
import java.time.Clock;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import dev.axonia.identity.api.ClaimsReader;
import dev.axonia.identity.api.IdentityClaims;
import dev.axonia.identity.api.spi.SignInObserver;
import dev.axonia.identity.application.UserService;
import dev.axonia.identity.configuration.UserProvisioningFilterConfig;
import dev.axonia.shared.api.cache.RecentlySeen;
import dev.axonia.shared.api.security.CurrentToken;

/**
 * Just-in-time provisioning: a stateless resource server has no login event, so the first authenticated request a user
 * makes upserts their profile from token claims. The recently-seen cache debounces the write - repeat requests within
 * the window are no-ops. SCIM (a later slice of 0006) writes into the same store ahead of time, demoting this filter to
 * a fallback.
 *
 * <p>
 * An Organization named by the token's {@code org} claim but absent from the registry, or not {@code ACTIVE}, is
 * rejected with 401/403 before this filter runs, by {@code OrganizationLifecycleFilter}, which reads the registry row
 * once and decides both existence and lifecycle from it. This filter therefore assumes a live Organization and only
 * provisions into it - which is also why it skips the same {@code /public/} prefix that gate exempts: those endpoints
 * are reachable anonymously and authorized by what the caller holds, not by their Organization's lifecycle, so nothing
 * upstream of this filter has confirmed the Organization is live there. Provisioning is not load-bearing on that
 * surface - every path that actually needs a profile (hub join, invite accept, {@code /axonia/me/organizations}) sits
 * under {@code /axonia} instead - so skipping it there is a no-op for real traffic and closes the gap.
 *
 * <p>
 * The same first request is told to the {@link SignInObserver}, around the upsert and inside the same window, because
 * what it writes down is written once and re-asserting it on every request would put a database read in front of every
 * page load for no gain. {@link SignInObserver#signedIn} has a window of its own, kept per Organization and subject, so
 * a hub sign-in soon after an enterprise one is not swallowed, and it is told a token that names no Organization too.
 * That window is claimed before the work rather than marked after it, so a page load's parallel requests make one call
 * between them, not one each.
 *
 * <p>
 * A delegated request provisions nothing. JIT provisioning exists to record the claims of the person actually signing
 * in, and somebody acting for them is not that: the person already has a profile, and the delegated token carries none
 * of the claims a profile is made of.
 *
 * <p>
 * Registered via {@link UserProvisioningFilterConfig} in the plain servlet chain at default (lowest) order - after
 * Spring Security's chain - so authentication is already established here. It is deliberately NOT a {@code @Component}:
 * that would make every {@code @WebMvcTest} controller slice (which auto-detects {@code Filter} beans) instantiate it
 * and fail on the absent {@code UserService}.
 */
public class UserProvisioningFilter extends OncePerRequestFilter {

    private static final String PUBLIC_PATH_PREFIX = "/public/";
    private static final int MAX_CACHE_SIZE = 10_000;

    private final UserService userService;
    private final SignInObserver observer;
    private final ClaimsReader claimsReader;
    private final RecentlySeen recentlySeen;
    private final RecentlySeen recentlySignedIn;

    public UserProvisioningFilter(UserService userService, SignInObserver observer, ClaimsReader claimsReader,
            Clock clock) {

        this.userService = userService;
        this.observer = observer;
        this.claimsReader = claimsReader;
        this.recentlySeen = new RecentlySeen(SignInObserver.RESEEN_AFTER, MAX_CACHE_SIZE, clock);
        this.recentlySignedIn = new RecentlySeen(SignInObserver.RESEEN_AFTER, MAX_CACHE_SIZE, clock);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(PUBLIC_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Somebody acting for the person is skipped outright. The synthesized delegated token carries only sub,
        // org and groups - no name, email or picture - so recording from it would rewrite the target's display name
        // to their raw subject, null their email, and stamp a last-seen the customer never earned.
        // An agent acting through a personal access token is skipped for the same reason: its synthesized token
        // carries the owner's sub and org but no name or email, and it is not the owner signing in.
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken
                && !CurrentToken.isDelegated() && !CurrentToken.isAgentToken()) {
            var claims = claimsReader.read();
            // A not-yet-scoped caller has no Organization to provision into. The observer's signedIn is all there is
            // to tell, and Organization-agnostic routes such as GET /me/organizations exist to serve exactly them.
            if (CurrentToken.hasOrganization()) {
                provisionOncePerWindow(claims);
            }
            if (recentlySignedIn.claim(claims.organizationId() + "|" + claims.userId())) {
                observer.signedIn(claims);
            }
        }
        filterChain.doFilter(request, response);
    }

    private void provisionOncePerWindow(IdentityClaims claims) {

        var key = claims.organizationId() + "|" + claims.userId();
        if (!recentlySeen.isDue(key)) {
            return;
        }
        observer.beforeProvisioning(claims);
        userService.provision(claims);
        observer.afterProvisioning(claims);
        recentlySeen.remember(key);
    }
}
