package dev.axonia.privacy.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import dev.axonia.access.api.OrganizationOwners;
import dev.axonia.community.api.Communities;
import dev.axonia.identity.api.spi.IdentityAccounts;
import dev.axonia.identity.api.spi.OrganizationMembershipSource;
import dev.axonia.identity.api.spi.OrganizationMembershipSource.OrganizationMembership;
import dev.axonia.organization.api.Organization;
import dev.axonia.organization.api.OrganizationRegistry;
import dev.axonia.privacy.api.spi.MemberDataRightsPolicy;
import dev.axonia.privacy.application.ErasureBlocker.BlockerKind;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.personaldata.ErasureCounts;
import dev.axonia.shared.api.personaldata.PersonDataEraser;
import dev.axonia.shared.api.personaldata.PersonErasure;
import dev.axonia.shared.api.tenancy.SubjectAuthentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class AccountErasureTest {

    private static final String SUBJECT = "user-1";
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");

    private final OrganizationMembershipSource memberships = mock(OrganizationMembershipSource.class);
    private final OrganizationRegistry organizations = mock(OrganizationRegistry.class);
    private final OrganizationOwners roleBindings = mock(OrganizationOwners.class);
    private final Communities communities = mock(Communities.class);
    private final MemberPurge purge = mock(MemberPurge.class);
    private final IdentityAccounts identityAccounts = mock(IdentityAccounts.class);
    private final RecordingPersonDataEraser personEraser = new RecordingPersonDataEraser();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final Set<String> exercisedByMembers = new HashSet<>();
    private final MemberDataRightsPolicy policy = organizationId -> Optional
            .of(exercisedByMembers.contains(organizationId));

    private final AccountErasure accountErasure = new AccountErasure(memberships, organizations,
            new MemberDataRights(List.of(policy)), roleBindings, communities, purge, identityAccounts,
            new PersonPurge(List.of(personEraser)), clock);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refusesAMemberOfAnEnterpriseOrganizationAndSaysWhy() {

        givenMemberships(hub("acme-hub"), enterprise("bigcorp"));

        var preflight = accountErasure.preflight(SUBJECT);

        assertThat(preflight.permitted()).isFalse();
        assertThat(preflight.blockers()).singleElement().satisfies(b -> {
            assertThat(b.kind()).isEqualTo(BlockerKind.ENTERPRISE_MEMBERSHIP);
            assertThat(b.organizationId()).isEqualTo("bigcorp");
        });
    }

    @Test
    void preflightIgnoresAMembershipTheLocalRegistryHasNoRowFor() {

        // given - the identity provider still lists a membership (e.g. a hub deleted locally but not yet cleaned up
        // there)
        var ghostId = "ghost-hub";
        when(organizations.organization(ghostId)).thenReturn(Optional.empty());
        givenMemberships(new OrganizationMembership(ghostId, ghostId));

        var preflight = accountErasure.preflight(SUBJECT);

        assertThat(preflight.permitted()).isTrue();
        assertThat(preflight.blockers()).isEmpty();
    }

    @Test
    void refusesTheSoleOwnerOfAHubAndNamesTheCommunityTheyMustDeleteFirst() {

        givenMemberships(hub("acme-hub"));
        givenSoleOwnerOf("acme-hub", "Rust Practitioners");

        var preflight = accountErasure.preflight(SUBJECT);

        assertThat(preflight.permitted()).isFalse();
        assertThat(preflight.blockers()).singleElement().satisfies(b -> {
            assertThat(b.kind()).isEqualTo(BlockerKind.SOLE_OWNER);
            assertThat(b.communityName()).isEqualTo("Rust Practitioners");
        });
    }

    @Test
    void permitsAPlainMemberOfHubsOnly() {

        givenMemberships(hub("acme-hub"), hub("other-hub"));

        assertThat(accountErasure.preflight(SUBJECT).permitted()).isTrue();
    }

    @Test
    void eraseRefusesWhenAGuardStillBites() {

        givenMemberships(enterprise("bigcorp"));

        assertThatThrownBy(() -> accountErasure.erase(SUBJECT)).isInstanceOf(ConflictException.class);

        // a refused erasure must destroy nothing - not one hub purged, not one person-rooted row, not the
        // identity-provider account
        verifyNoInteractions(purge, identityAccounts);
        assertThat(personEraser.calls).isZero();
    }

    /**
     * The refusal message reaches a log or a 409 error body verbatim. It still names the blocker's enum constant and
     * raw organization id - it is not user-facing copy - but it must not be {@code ErasureBlocker}'s record
     * {@code toString()}, which would leak a Java type name and field syntax on top of that, e.g.
     * {@code [ErasureBlocker[kind=ENTERPRISE_MEMBERSHIP, organizationId=bigcorp, communityName=null]]}.
     */
    @Test
    void eraseMessageDescribesBlockersInsteadOfDumpingTheRecord() {

        givenMemberships(enterprise("bigcorp"));

        assertThatThrownBy(() -> accountErasure.erase(SUBJECT)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("ENTERPRISE_MEMBERSHIP").hasMessageContaining("bigcorp")
                .hasMessageNotContaining("ErasureBlocker[");
    }

    /**
     * Same fix, exercised on the other blocker kind so both branches of the description (with and without a community
     * name) run under {@code erase}, not only under {@code preflight}.
     */
    @Test
    void eraseMessageNamesTheCommunityForASoleOwnerBlocker() {

        givenMemberships(hub("acme-hub"));
        givenSoleOwnerOf("acme-hub", "Rust Practitioners");

        assertThatThrownBy(() -> accountErasure.erase(SUBJECT)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("SOLE_OWNER").hasMessageContaining("acme-hub")
                .hasMessageContaining("Rust Practitioners").hasMessageNotContaining("ErasureBlocker[");
    }

    @Test
    void eraseWrapsAnIdentityProviderFailureNamingBothHalves() {

        givenMemberships(hub("acme-hub"), hub("other-hub"));
        doThrow(new RuntimeException("idp down")).when(identityAccounts).deleteAccount(SUBJECT);

        assertThatThrownBy(() -> accountErasure.erase(SUBJECT)).isInstanceOf(AccountErasureIncompleteException.class)
                .hasMessageContaining(SUBJECT).hasMessageContaining("2 Organization")
                .hasCauseInstanceOf(RuntimeException.class);

        // the database half already committed for both hubs before the identity-provider call ran
        verify(purge).eraseFrom("acme-hub", SUBJECT, NOW);
        verify(purge).eraseFrom("other-hub", SUBJECT, NOW);
    }

    /**
     * The mapped 502 tells the caller their data is gone and only sign-out remains, but nobody but the caller can act
     * on that. This is the only record an operator has that an identity provider account was orphaned - it must survive
     * even though {@code @ExceptionHandler}-resolved exceptions are only traced at DEBUG by Spring's own resolver.
     */
    @Test
    void eraseLogsTheIdentityProviderFailureWithItsCauseBeforeWrapping(CapturedOutput output) {

        givenMemberships(hub("acme-hub"), hub("other-hub"));
        var identityProviderFailure = new RuntimeException("idp down");
        doThrow(identityProviderFailure).when(identityAccounts).deleteAccount(SUBJECT);

        assertThatThrownBy(() -> accountErasure.erase(SUBJECT)).isInstanceOf(AccountErasureIncompleteException.class);

        assertThat(output.getOut()).contains(SUBJECT).contains("2 Organization").contains("idp down");
    }

    @Test
    void eraseRunsEveryHubThenDeletesTheIdentityProviderAccountLast() {

        givenMemberships(hub("acme-hub"), hub("other-hub"));

        accountErasure.erase(SUBJECT);

        verify(purge).eraseFrom("acme-hub", SUBJECT, NOW);
        verify(purge).eraseFrom("other-hub", SUBJECT, NOW);
        var order = inOrder(purge, identityAccounts);
        order.verify(purge).eraseFrom("acme-hub", SUBJECT, NOW);
        order.verify(purge).eraseFrom("other-hub", SUBJECT, NOW);
        order.verify(identityAccounts).deleteAccount(SUBJECT);
    }

    /**
     * The person axis, and the two things about it a per-Organization loop cannot give. It runs exactly once rather
     * than once per hub - a standing outlives any one of them - and it runs at all for somebody who belongs to none,
     * whose person-rooted rows would otherwise survive their own account erasure.
     */
    @Test
    void erasesPersonRootedDataExactlyOnceEvenAcrossSeveralHubs() {

        givenMemberships(hub("acme-hub"), hub("other-hub"));

        accountErasure.erase(SUBJECT);

        assertThat(personEraser.calls).isOne();
        assertThat(personEraser.erasure.subject()).isEqualTo(SUBJECT);
        assertThat(personEraser.erasure.erasedAt()).isEqualTo(NOW);
        assertThat(personEraser.erasure.surrogateId()).startsWith("erased:");
    }

    @Test
    void erasesPersonRootedDataForSomebodyWhoBelongsToNoHubAtAll() {

        givenMemberships();

        accountErasure.erase(SUBJECT);

        assertThat(personEraser.calls).isOne();
        verifyNoInteractions(purge);
        verify(identityAccounts).deleteAccount(SUBJECT);
    }

    /**
     * The whole point of the step. The tables it erases are scoped on {@code app.current_subject}, which the datasource
     * reads from the authentication in force when the transaction acquires its connection - so an eraser invoked
     * outside {@code SubjectScope.runAs} matches zero rows, silently, and stays green under any connection that owns
     * the tables. Asserting on the authentication at call time is the only thing a mock-level test can prove here;
     * {@code StandingRlsStackIsolationTest} proves the rows actually move, under {@code axonia_app}.
     */
    @Test
    void personRootedErasureRunsInsideTheSubjectsOwnScope() {

        givenMemberships(hub("acme-hub"));

        accountErasure.erase(SUBJECT);

        assertThat(personEraser.authentication).isInstanceOf(SubjectAuthentication.class);
        assertThat(((SubjectAuthentication) personEraser.authentication).subject()).isEqualTo(SUBJECT);
    }

    private void givenMemberships(OrganizationMembership... organizationMemberships) {

        when(memberships.membershipsFor(SUBJECT)).thenReturn(List.of(organizationMemberships));
    }

    /** An Organization whose members exercise their own data rights, as a Community Hub's do. */
    private OrganizationMembership hub(String id) {

        exercisedByMembers.add(id);
        return enterprise(id);
    }

    /** An Organization that exercises its members' data rights itself: the Enterprise answer. */
    private OrganizationMembership enterprise(String id) {

        when(organizations.organization(id)).thenReturn(Optional.of(Organization.builder().id(id).name(id).build()));
        return new OrganizationMembership(id, id);
    }

    private void givenSoleOwnerOf(String hubId, String communityName) {

        when(roleBindings.isSoleOwner(hubId, SUBJECT)).thenReturn(true);
        when(communities.hubCommunityName(hubId)).thenReturn(Optional.of(communityName));
    }

    /**
     * A hand-written fake rather than a Mockito mock: the assertion is about the security context at the moment of the
     * call, which a stubbed answer can capture but a verified interaction cannot.
     */
    private static final class RecordingPersonDataEraser implements PersonDataEraser {

        private int calls;
        private PersonErasure erasure;
        private Authentication authentication;

        @Override
        public ErasureCounts erase(PersonErasure personErasure) {

            calls++;
            erasure = personErasure;
            authentication = SecurityContextHolder.getContext().getAuthentication();
            return new ErasureCounts(1, 0, 1, 0);
        }
    }
}
