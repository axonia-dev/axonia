package dev.axonia.identity.internal;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.CurrentToken;
import dev.axonia.SteppableClock;
import dev.axonia.identity.ClaimsReader;
import dev.axonia.identity.IdentityClaims;
import dev.axonia.identity.SignInObserver;
import dev.axonia.identity.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class UserProvisioningFilterTest {

    private static final IdentityClaims ALICE = IdentityClaims.builder().userId("alice-sub").organizationId("default")
            .displayName("Alice Anderson").email("alice@axonia.example").build();

    private static final IdentityClaims NOMAD = IdentityClaims.builder().userId("nomad-sub").displayName("Nomad Nobody")
            .email("nomad@example.test").build();

    @Mock
    private UserService userService;

    @Mock
    private ClaimsReader claimsReader;

    @Mock
    private SignInObserver observer;

    private final SteppableClock clock = new SteppableClock(Instant.parse("2026-10-01T08:00:00Z"));

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void provisionsTheCallerOnFirstAuthenticatedRequest() throws Exception {

        // given
        authenticate("alice-sub");
        when(claimsReader.read()).thenReturn(ALICE);
        var sut = new UserProvisioningFilter(userService, observer, claimsReader, clock);
        var chain = new MockFilterChain();

        // when
        sut.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        // then
        verify(userService).provision(ALICE);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void debouncesRepeatRequestsFromTheSameUser() throws Exception {

        // given
        authenticate("alice-sub");
        when(claimsReader.read()).thenReturn(ALICE);
        var sut = new UserProvisioningFilter(userService, observer, claimsReader, clock);
        sut.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        // when
        sut.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        // then
        verify(userService, times(1)).provision(ALICE);
        verify(observer, times(1)).beforeProvisioning(ALICE);
        verify(observer, times(1)).afterProvisioning(ALICE);
        verify(observer, times(1)).signedIn(ALICE);
    }

    @Test
    void skipsUnauthenticatedRequests() throws Exception {

        // given no authentication in the security context
        var sut = new UserProvisioningFilter(userService, observer, claimsReader, clock);
        var chain = new MockFilterChain();

        // when
        sut.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        // then
        verify(userService, never()).provision(ALICE);
        verifyNoInteractions(observer);
        assertThat(chain.getRequest()).isNotNull();
    }

    /**
     * The window is measured on the application's clock, so a returning member is provisioned, and their sign-in told,
     * again once it has passed - not after ten minutes of the server's wall clock.
     */
    @Test
    void provisionsAndTellsTheSignInAgainOnceTheWindowHasPassed() throws Exception {

        // given
        authenticate("alice-sub");
        when(claimsReader.read()).thenReturn(ALICE);
        var sut = new UserProvisioningFilter(userService, observer, claimsReader, clock);
        sut.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
        clock.advance(SignInObserver.RESEEN_AFTER);

        // when
        sut.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        // then
        verify(userService, times(2)).provision(ALICE);
        verify(observer, times(2)).signedIn(ALICE);
    }

    /**
     * The ordering is the observer's to rely on: what it writes before the upsert may compare the address the member
     * held with the one they arrive with, and one statement later both are the same one. What it settles on every
     * sign-in comes last, so a first sign-in already counts the profile it has just written.
     */
    @Test
    void tellsTheObserverAroundTheUpsertAndThenOfTheSignIn() throws Exception {

        // given
        authenticate("alice-sub");
        when(claimsReader.read()).thenReturn(ALICE);
        var sut = new UserProvisioningFilter(userService, observer, claimsReader, clock);

        // when
        sut.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        // then
        var order = inOrder(observer, userService);
        order.verify(observer).beforeProvisioning(ALICE);
        order.verify(userService).provision(ALICE);
        order.verify(observer).afterProvisioning(ALICE);
        order.verify(observer).signedIn(ALICE);
    }

    /**
     * A person who signs in and joins nothing has no Organization to be provisioned into, and is still somebody who
     * signed in.
     */
    @Test
    void tellsTheObserverOfASignInThatNamesNoOrganizationButProvisionsNoProfile() throws Exception {

        // given an authenticated caller whose token has no active org (e.g. a not-yet-scoped user)
        authenticateWithoutOrg("nomad-sub");
        when(claimsReader.read()).thenReturn(NOMAD);
        var sut = new UserProvisioningFilter(userService, observer, claimsReader, clock);
        var chain = new MockFilterChain();

        // when
        sut.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        // then the sign-in is told, no profile is provisioned, and the request flows through - no spurious 401
        verify(observer).signedIn(NOMAD);
        verify(observer, never()).beforeProvisioning(any());
        verify(observer, never()).afterProvisioning(any());
        verify(userService, never()).provision(any());
        assertThat(chain.getRequest()).isNotNull();
    }

    /**
     * The sign-in window is kept per Organization, so a person who switches Organization soon after a sign-in is told
     * again from the second one.
     */
    @Test
    void aSignInToAnotherOrganizationInsideTheWindowIsStillTold() throws Exception {

        // given
        var aliceInAHub = IdentityClaims.builder().userId("alice-sub").organizationId("a-hub")
                .displayName("Alice Anderson").email("alice@axonia.example").build();
        when(claimsReader.read()).thenReturn(ALICE, aliceInAHub);
        var sut = new UserProvisioningFilter(userService, observer, claimsReader, clock);
        authenticate("alice-sub");
        sut.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        // when
        authenticateIn("alice-sub", "a-hub");
        sut.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        // then
        verify(observer, times(1)).signedIn(ALICE);
        verify(observer, times(1)).signedIn(aliceInAHub);
    }

    /**
     * A page load fires several requests at once. The sign-in window is claimed before the observer is told, so a
     * request arriving while it is still being told skips instead of telling it again.
     */
    @Test
    void aRequestArrivingWhileTheSignInIsBeingToldDoesNotTellItAgain() throws Exception {

        // given
        authenticateWithoutOrg("nomad-sub");
        when(claimsReader.read()).thenReturn(NOMAD);
        var sut = new UserProvisioningFilter(userService, observer, claimsReader, clock);
        doAnswer(told -> {
            sut.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
            return null;
        }).when(observer).signedIn(NOMAD);

        // when
        sut.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        // then
        verify(observer, times(1)).signedIn(NOMAD);
    }

    /**
     * An agent acting through a personal access token is not its owner signing in, and its synthesized token carries no
     * email or name: recording from it would blank the person's email and rename them to their subject.
     */
    @Test
    void anAgentTokenTellsNothingAndProvisionsNothing() throws Exception {

        // given
        authenticateAsAgent("alice-sub");
        var sut = new UserProvisioningFilter(userService, observer, claimsReader, clock);

        // when
        sut.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        // then
        verifyNoInteractions(observer);
        verify(userService, never()).provision(any());
        verify(claimsReader, never()).read();
    }

    @Test
    void neverProvisionsOnADelegatedRequest() throws Exception {

        // given - somebody acting for the person: the synthesized delegated token carries no name, email or picture,
        // so recording from it would overwrite the person's real profile with a raw subject and a null email
        authenticateAsDelegated("alice-sub");
        var sut = new UserProvisioningFilter(userService, observer, claimsReader, clock);
        var chain = new MockFilterChain();

        // when
        sut.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        // then - the person's profile is left exactly as their own last sign-in wrote it, and nothing is told
        verify(claimsReader, never()).read();
        verify(userService, never()).provision(any());
        verifyNoInteractions(observer);
        assertThat(chain.getRequest()).isNotNull();
    }

    /**
     * The {@code /public/} surface is exempt from {@code OrganizationLifecycleFilter}, so nothing upstream of this
     * filter has confirmed the Organization named by the token is live there. Provisioning must therefore skip it too -
     * otherwise a stranger whose Organization is suspended (or deleted) gets a profile silently written on their very
     * first request, on a route where nothing actually needs one.
     */
    @Test
    void skipsProvisioningOnThePublicSurface() throws Exception {

        // given an authenticated caller reaching a /public/ route
        authenticate("alice-sub");
        var sut = new UserProvisioningFilter(userService, observer, claimsReader, clock);
        var request = new MockHttpServletRequest("GET", "/public/hubs");
        var chain = new MockFilterChain();

        // when
        sut.doFilter(request, new MockHttpServletResponse(), chain);

        // then no claims are even read, and the request still flows through
        verify(claimsReader, never()).read();
        verify(userService, never()).provision(any());
        verifyNoInteractions(observer);
        assertThat(chain.getRequest()).isNotNull();
    }

    private static void authenticate(String subject) {

        authenticateIn(subject, "default");
    }

    private static void authenticateIn(String subject, String organizationId) {

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).claim("org", organizationId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static void authenticateAsAgent(String subject) {

        var jwt = Jwt.withTokenValue("axp-session").header("alg", "none").subject(subject).claim("org", "default")
                .claim("tokenId", 7L).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static void authenticateAsDelegated(String subject) {

        var jwt = Jwt.withTokenValue("delegated-session").header("alg", "none").subject(subject).claim("org", "default")
                .claim(CurrentToken.ACTOR_CLAIM, Map.of("sub", "actor-1")).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static void authenticateWithoutOrg(String subject) {

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
