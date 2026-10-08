package dev.axonia.privacy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import dev.axonia.ConflictException;
import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.CurrentToken;
import dev.axonia.ExportScope;
import dev.axonia.FakeRateLimitStore;
import dev.axonia.RateLimiters;
import dev.axonia.organization.Organization;
import dev.axonia.organization.OrganizationService;
import dev.axonia.privacy.ErasureBlocker.BlockerKind;
import dev.axonia.privacy.internal.ExportLabels;
import dev.axonia.privacy.internal.ExportPackageNames;
import dev.axonia.privacy.internal.MemberDataRights;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@CoreWebMvcTest(controllers = PrivacyController.class)
@Import({ExportDownload.class, ExportPackageNames.class, PrivacyControllerTest.FixedClockConfig.class})
class PrivacyControllerTest extends ControllerTestSupport {

    private static final String DEFAULT_TEST_ORG = "default";

    @MockitoBean
    private AccountErasure accountErasure;

    @MockitoBean
    private DataExport dataExport;

    @MockitoBean
    private OrganizationService organizations;

    @MockitoBean
    private MemberDataRights memberDataRights;

    @MockitoBean
    private ExportLabels exportLabels;

    @Test
    void preflightReportsBlockersWithoutErasingAnything() {

        // given
        when(accountErasure.preflight(DEFAULT_TEST_USER)).thenReturn(new ErasurePreflight(false,
                List.of(new ErasureBlocker(BlockerKind.SOLE_OWNER, "acme-hub", "Rust Practitioners"))));

        // when
        var actual = mvc.get().uri("/axonia/me/account/erasure-preflight");

        // then
        actual.assertThat().hasStatus(200).bodyJson().extractingPath("$.permitted").isEqualTo(false);
        actual.assertThat().bodyJson().extractingPath("$.blockers[0].communityName").isEqualTo("Rust Practitioners");
        verify(accountErasure, never()).erase(any());
    }

    /**
     * {@code DEFAULT_TEST_USER} is also the forged default subject, so asserting against it alone would pass even if
     * the controller hardcoded that literal instead of reading the token. Forges a distinct subject, and rides a decoy
     * {@code ?subject=} query parameter the controller has no parameter to bind - pinning that the id can only ever
     * come from the token, not from anywhere an attacker could reach.
     */
    @Test
    void deleteErasesTheCallersOwnAccountAndNobodyElses() {

        // when
        var actual = mvc.delete().uri("/axonia/me/account").param("subject", "victim")
                .with(jwt().jwt(token -> token.subject("real-caller").claim("org", "acme")));

        // then
        actual.assertThat().hasStatus(204);
        verify(accountErasure).erase("real-caller");
        verify(accountErasure, never()).erase("victim");
    }

    @Test
    void deleteIsRejectedForAnAnonymousCaller() {

        // when
        var actual = mvc.delete().uri("/axonia/me/account").with(anonymous());

        // then
        actual.assertThat().hasStatus(401);
    }

    /**
     * The most likely real-world failure of this endpoint: a guard still bites on the re-check inside
     * {@code AccountErasure.erase}. Pins that {@link ConflictException} reaches the root {@code GlobalExceptionHandler}
     * as 409 rather than being shadowed - or swallowed - by {@link PrivacyExceptionHandler}, which is registered on
     * this same controller for a different exception type.
     */
    @Test
    void deleteMapsAStillBitingGuardToConflict() {

        // given
        doThrow(new ConflictException("ACCOUNT_ERASURE_BLOCKED", "Account cannot be erased: SOLE_OWNER (acme-hub)"))
                .when(accountErasure).erase(DEFAULT_TEST_USER);

        // when
        var actual = mvc.delete().uri("/axonia/me/account");

        // then
        actual.assertThat().hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("ACCOUNT_ERASURE_BLOCKED");
    }

    @Test
    void deleteMapsAnIncompleteErasureToBadGateway() {

        // given - every Organization's data is already erased; only the identity-provider deletion remains
        doThrow(new AccountErasureIncompleteException(DEFAULT_TEST_USER, 2, new RuntimeException("kc down")))
                .when(accountErasure).erase(DEFAULT_TEST_USER);

        // when
        var actual = mvc.delete().uri("/axonia/me/account");

        // then - not 500: the data really is gone, only a retryable cleanup step is outstanding
        actual.assertThat().hasStatus(502).bodyJson().extractingPath("$.code").isEqualTo("ACCOUNT_ERASURE_INCOMPLETE");
    }

    /**
     * A delegated session must never be able to erase the account it acts in - the person never consented, and acting
     * for somebody is not acting on their data-subject rights. Distinct status and code from
     * {@code ACCOUNT_ERASURE_BLOCKED}'s 409: that guard is about the person's own memberships and its remedy is "leave
     * the community first"; this one's remedy is "sign in as yourself".
     *
     * <p>
     * {@code verifyNoInteractions} rather than {@code verify(..., never()).erase(any())}: the stronger assertion also
     * catches a future refactor that reads the preflight before refusing, not only one that goes on to erase.
     */
    @Test
    void deleteRefusesADelegatedSessionWithoutErasingAnything() {

        // when
        var actual = mvc.delete().uri("/axonia/me/account").with(delegatedSession());

        // then
        actual.assertThat().hasStatus(403).bodyJson().extractingPath("$.code").isEqualTo("REQUIRES_OWN_SESSION");
        verifyNoInteractions(accountErasure);
    }

    /**
     * The preflight refuses too, not just the erasure itself: it would otherwise hand whoever acts in a delegated
     * session the person's blocker list - which hub they solely own, which enterprise they belong to - for an action
     * that endpoint can never actually let them take.
     */
    @Test
    void preflightRefusesADelegatedSessionWithoutReportingAnything() {

        // when
        var actual = mvc.get().uri("/axonia/me/account/erasure-preflight").with(delegatedSession());

        // then
        actual.assertThat().hasStatus(403).bodyJson().extractingPath("$.code").isEqualTo("REQUIRES_OWN_SESSION");
        verifyNoInteractions(accountErasure);
    }

    private static RequestPostProcessor delegatedSession() {

        return jwt().jwt(token -> token.subject(DEFAULT_TEST_USER).claim("org", "acme").claim(CurrentToken.ACTOR_CLAIM,
                Map.of("sub", "actor-1")));
    }

    @Test
    void exportMyDataStreamsAZipWhereMembersExerciseTheirOwnDataRights() throws Exception {

        // given
        givenTheOrganization(true);

        // when
        var actual = mvc.get().uri("/axonia/me/data-export");

        // then
        actual.assertThat().hasStatus(200).hasHeader("Content-Type", "application/zip").hasHeader("Content-Disposition",
                "attachment; filename=\"axonia-export-Default-Engineering-Hub-2026-07-27.zip\"");
        verify(dataExport).writeTo(eq(new ExportScope.OneMember(DEFAULT_TEST_ORG, DEFAULT_TEST_USER)), any());
    }

    /**
     * Matching the erasure surface: where the Organization exercises its members' data rights, as an enterprise's
     * administrator does, the member does not export it themselves.
     */
    @Test
    void exportMyDataRefusesWhereTheOrganizationExercisesTheRights() {

        // given
        givenTheOrganization(false);

        // when
        var actual = mvc.get().uri("/axonia/me/data-export");

        // then
        actual.assertThat().hasStatus(403);
        verifyNoInteractions(dataExport);
    }

    /**
     * An Organization the registry does not hold exercises nothing, so its policy is never asked and the member's own
     * export is not refused; the package, with no name to go by, takes the generic one.
     */
    @Test
    void exportMyDataIsNotRefusedForAnOrganizationTheRegistryDoesNotHold() throws Exception {

        // given
        when(organizations.organization(DEFAULT_TEST_ORG)).thenReturn(Optional.empty());

        // when
        var actual = mvc.get().uri("/axonia/me/data-export");

        // then
        actual.assertThat().hasStatus(200).hasHeader("Content-Disposition",
                "attachment; filename=\"axonia-export-organization-2026-07-27.zip\"");
        verify(dataExport).writeTo(eq(new ExportScope.OneMember(DEFAULT_TEST_ORG, DEFAULT_TEST_USER)), any());
        verifyNoInteractions(memberDataRights);
    }

    /**
     * The same guard that refuses erasure and its preflight to a delegated session must refuse this endpoint too -
     * {@link PrivacyOwnSignInGuard} is generalised to every handler in this module precisely so a new one like this
     * cannot slip through unguarded.
     */
    @Test
    void exportMyDataRefusesADelegatedSessionWithoutExportingAnything() {

        // when
        var actual = mvc.get().uri("/axonia/me/data-export").with(delegatedSession());

        // then
        actual.assertThat().hasStatus(403).bodyJson().extractingPath("$.code").isEqualTo("REQUIRES_OWN_SESSION");
        verifyNoInteractions(dataExport);
    }

    /**
     * A token that names no Organization belongs to somebody who signed in and joined nothing. They are not refused -
     * axonia still holds their account - and nothing about their export is looked up in an Organization they do not
     * have. The dev profile's fallback resolves a missing claim to {@code default} for Organization-scoped reads; it
     * must not decide who this person is, so it is on here and the export is still theirs alone.
     */
    @Test
    void exportMyDataHandsAPersonWithNoOrganizationTheirOwnData() throws Exception {

        // given
        CurrentToken.setAllowDefaultFallback(true);

        // when
        var actual = mvc.get().uri("/axonia/me/data-export").with(jwt().jwt(token -> token.subject("nomad")));

        // then
        actual.assertThat().hasStatus(200).hasHeader("Content-Disposition",
                "attachment; filename=\"axonia-export-personal-2026-07-27.zip\"");
        verify(dataExport).writeTo(eq(new ExportScope.Person("nomad")), any());
        verifyNoInteractions(organizations);
    }

    @Test
    void deleteErasesAPersonWithNoOrganization() {

        // given
        CurrentToken.setAllowDefaultFallback(false);

        // when
        var actual = mvc.delete().uri("/axonia/me/account").with(jwt().jwt(token -> token.subject("nomad")));

        // then
        actual.assertThat().hasStatus(204);
        verify(accountErasure).erase("nomad");
    }

    @AfterEach
    void restoreTheDevFallback() {

        CurrentToken.setAllowDefaultFallback(false);
    }

    private void givenTheOrganization(boolean exercisedByMembers) {

        when(organizations.organization(DEFAULT_TEST_ORG)).thenReturn(
                Optional.of(Organization.builder().id(DEFAULT_TEST_ORG).name("Default Engineering Hub").build()));
        when(memberDataRights.exercisedByMembers(DEFAULT_TEST_ORG)).thenReturn(exercisedByMembers);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {

        @Bean
        Clock clock() {

            return Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        RateLimiters rateLimiters() {

            return new RateLimiters(new FakeRateLimitStore(), new SimpleMeterRegistry());
        }
    }
}
