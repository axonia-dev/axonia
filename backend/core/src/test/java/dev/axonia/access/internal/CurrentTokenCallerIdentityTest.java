package dev.axonia.access.internal;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import dev.axonia.CurrentToken;
import dev.axonia.identity.DirectoryGroups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class CurrentTokenCallerIdentityTest {

    @Mock
    private DirectoryGroups directoryGroups;

    @AfterEach
    void clearContexts() {

        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void groups_claimPresent_readsTheClaimAndNeverTheDirectory() {

        // given
        authenticate("sub-1", List.of("Engineering"));
        var sut = new CurrentTokenCallerIdentity(directoryGroups);

        // when
        var actual = sut.groups();

        // then
        assertThat(actual).containsExactly("Engineering");
        verifyNoInteractions(directoryGroups);
    }

    @Test
    void groups_claimEmpty_supplementsFromTheSyncedDirectory() {

        // given
        authenticate("sub-1", List.of());
        when(directoryGroups.reconciliationKeysSubstitutingForAbsentClaim("acme", "sub-1"))
                .thenReturn(List.of("group-guid-1"));
        var sut = new CurrentTokenCallerIdentity(directoryGroups);

        // when
        var actual = sut.groups();

        // then
        assertThat(actual).containsExactly("group-guid-1");
    }

    /**
     * Also the browser-session case: this token carries no {@code tokenId} claim, so the agent guard
     * ({@link CurrentToken#isAgentToken()}) is false here and the substitution for an absent claim runs exactly as it
     * did before that guard existed.
     */
    @Test
    void groups_claimAbsent_supplementsFromTheSyncedDirectory_unaffectedByTheAgentGuard() {

        // given
        authenticateWithoutGroupsClaim();
        when(directoryGroups.reconciliationKeysSubstitutingForAbsentClaim("acme", "sub-1"))
                .thenReturn(List.of("group-guid-2"));
        var sut = new CurrentTokenCallerIdentity(directoryGroups);

        // when
        var actual = sut.groups();

        // then
        assertThat(actual).containsExactly("group-guid-2");
    }

    /**
     * An agent acts with the permissions bound to the person who minted its token, and with none of the permissions
     * their directory groups confer. A personal access token is minted by a person who may hold SCIM_WRITE without
     * holding ROLE_ASSIGN, and the SCIM client chooses its own group keys, so letting a directory group speak for an
     * agent turns "can provision" into "can be Owner".
     *
     * <p>
     * The directory is stubbed to answer with a real membership (not Mockito's default empty list) so the assertion is
     * load-bearing on its own: without the guard, {@code groups()} would reach this stub and return
     * {@code ["platform-admins"]}, not merely fail a verify. {@code lenient()} is required because the fixed production
     * code never calls the stub at all - the guard returns before the directory is consulted - and strict stubbing
     * would otherwise fail this test for an unused stub.
     */
    @Test
    void anAgentTokenResolvesNoGroupsHoweverManyTheDirectoryRecords() {

        // given
        lenient().when(directoryGroups.reconciliationKeysSubstitutingForAbsentClaim("acme", "u-1"))
                .thenReturn(List.of("platform-admins"));
        authenticateWith(tokenBuilder("u-1").claim("org", "acme").claim("tokenId", 7L));
        var sut = new CurrentTokenCallerIdentity(directoryGroups);

        // when
        var actual = sut.groups();

        // then
        assertThat(actual).isEmpty();
        verifyNoInteractions(directoryGroups);
    }

    @Test
    void groups_repeatedGateCallsWithinOneRequest_readsTheDirectoryOnce() {

        // given
        beginRequest();
        authenticate("sub-1", List.of());
        when(directoryGroups.reconciliationKeysSubstitutingForAbsentClaim("acme", "sub-1"))
                .thenReturn(List.of("group-guid-1"));
        var sut = new CurrentTokenCallerIdentity(directoryGroups);

        // when
        var actual = List.of(sut.groups(), sut.groups(), sut.groups());

        // then
        assertThat(actual).allSatisfy(groups -> assertThat(groups).containsExactly("group-guid-1"));
        verify(directoryGroups, times(1)).reconciliationKeysSubstitutingForAbsentClaim("acme", "sub-1");
    }

    @Test
    void groups_differentSubjectWithinOneRequest_neverServesTheOtherCallersGroups() {

        // given - a memoized value must never be handed to a different subject
        beginRequest();
        when(directoryGroups.reconciliationKeysSubstitutingForAbsentClaim("acme", "sub-1"))
                .thenReturn(List.of("group-of-one"));
        when(directoryGroups.reconciliationKeysSubstitutingForAbsentClaim("acme", "sub-2"))
                .thenReturn(List.of("group-of-two"));
        var sut = new CurrentTokenCallerIdentity(directoryGroups);
        authenticate("sub-1", List.of());
        sut.groups();

        // when
        authenticate("sub-2", List.of());
        var actual = sut.groups();

        // then
        assertThat(actual).containsExactly("group-of-two");
    }

    @Test
    void groups_differentOrganizationWithinOneRequest_neverServesTheOtherOrganizationsGroups() {

        // given
        beginRequest();
        when(directoryGroups.reconciliationKeysSubstitutingForAbsentClaim("acme", "sub-1"))
                .thenReturn(List.of("acme-group"));
        when(directoryGroups.reconciliationKeysSubstitutingForAbsentClaim("globex", "sub-1"))
                .thenReturn(List.of("globex-group"));
        var sut = new CurrentTokenCallerIdentity(directoryGroups);
        authenticateWith(tokenBuilder("sub-1").claim("org", "acme").claim("groups", List.of()));
        sut.groups();

        // when
        authenticateWith(tokenBuilder("sub-1").claim("org", "globex").claim("groups", List.of()));
        var actual = sut.groups();

        // then
        assertThat(actual).containsExactly("globex-group");
    }

    @Test
    void groups_nextRequest_doesNotReuseThePreviousRequestsResolvedGroups() {

        // given
        beginRequest();
        authenticate("sub-1", List.of());
        when(directoryGroups.reconciliationKeysSubstitutingForAbsentClaim("acme", "sub-1"))
                .thenReturn(List.of("group-guid-1"));
        var sut = new CurrentTokenCallerIdentity(directoryGroups);
        sut.groups();

        // when
        beginRequest();
        var actual = sut.groups();

        // then
        assertThat(actual).containsExactly("group-guid-1");
        verify(directoryGroups, times(2)).reconciliationKeysSubstitutingForAbsentClaim("acme", "sub-1");
    }

    @Test
    void groups_outsideAnyRequest_stillResolvesFromTheDirectory() {

        // given - scheduled and async work runs with no request attributes bound
        authenticate("sub-1", List.of());
        when(directoryGroups.reconciliationKeysSubstitutingForAbsentClaim("acme", "sub-1"))
                .thenReturn(List.of("group-guid-1"));
        var sut = new CurrentTokenCallerIdentity(directoryGroups);

        // when
        var actual = List.of(sut.groups(), sut.groups());

        // then
        assertThat(actual).allSatisfy(groups -> assertThat(groups).containsExactly("group-guid-1"));
        verify(directoryGroups, times(2)).reconciliationKeysSubstitutingForAbsentClaim("acme", "sub-1");
    }

    private static void beginRequest() {

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    private static void authenticate(String subject, List<String> groups) {

        authenticateWith(tokenBuilder(subject).claim("org", "acme").claim("groups", groups));
    }

    private static void authenticateWithoutGroupsClaim() {

        authenticateWith(tokenBuilder("sub-1").claim("org", "acme"));
    }

    private static Jwt.Builder tokenBuilder(String subject) {

        return Jwt.withTokenValue("token").header("alg", "none").subject(subject);
    }

    private static void authenticateWith(Jwt.Builder builder) {

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build()));
    }
}
