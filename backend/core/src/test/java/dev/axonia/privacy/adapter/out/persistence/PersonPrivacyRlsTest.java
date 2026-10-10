package dev.axonia.privacy.adapter.out.persistence;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import dev.axonia.CoreSpringBootTest;
import dev.axonia.RoleConnectedTestSupport;
import dev.axonia.identity.api.spi.IdentityAccounts;
import dev.axonia.identity.api.spi.OrganizationMembershipSource;
import dev.axonia.shared.api.security.CurrentToken;

import static dev.axonia.privacy.adapter.out.persistence.PersonPrivacyFixtures.COMMUNITY_SLUG;
import static dev.axonia.privacy.adapter.out.persistence.PersonPrivacyFixtures.ONBOARDING_PATH;
import static dev.axonia.privacy.adapter.out.persistence.PersonPrivacyFixtures.ORGANIZATION;
import static dev.axonia.privacy.adapter.out.persistence.PersonPrivacyFixtures.ORGANIZATION_PROFILE_NAME;
import static dev.axonia.privacy.adapter.out.persistence.PersonPrivacyFixtures.SUBJECT;
import static dev.axonia.privacy.adapter.out.persistence.PersonPrivacyFixtures.TENANT_PROFILE;
import static dev.axonia.privacy.adapter.out.persistence.PersonPrivacyFixtures.entryNamed;
import static dev.axonia.privacy.adapter.out.persistence.PersonPrivacyFixtures.signedInWithNoOrganization;
import static dev.axonia.privacy.adapter.out.persistence.PersonPrivacyFixtures.unzip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A person whose token names no Organization - who signed in and joined nothing, or left every hub - exercising both
 * data-subject routes through the real request stack as {@code axonia_app}. Every person-rooted table is isolated on
 * {@code app.current_subject}, which the Testcontainers owner would bypass, so a scope the export or the erasure forgot
 * to enter would read and delete nothing here and still pass there.
 *
 * <p>
 * The person also holds a profile in an Organization they are not signed in to. It is the needle for the other half of
 * the promise: a person's export carries only what is rooted at the person, never a row that belongs to a tenant.
 *
 * <p>
 * The DEV profile resolves an org-less token to the {@code default} Organization, which would route both requests down
 * the member path this class exists to avoid, so the fallback is off for every test here and restored after.
 * {@code CloudPersonPrivacyRlsTest} adds the person-rooted rows only closed modules hold.
 */
@CoreSpringBootTest
@AutoConfigureMockMvc
class PersonPrivacyRlsTest extends RoleConnectedTestSupport {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private OrganizationMembershipSource memberships;

    @MockitoBean
    private IdentityAccounts identityAccounts;

    @BeforeEach
    void seedAPersonWithNoOrganization() throws SQLException {

        CurrentToken.setAllowDefaultFallback(false);
        when(memberships.membershipsFor(SUBJECT)).thenReturn(List.of());
        asOwner(ONBOARDING_PATH);
        asOwner(TENANT_PROFILE);
    }

    @AfterEach
    void removeWhatThisClassWrote() throws SQLException {

        CurrentToken.setAllowDefaultFallback(true);
        SecurityContextHolder.clearContext();
        asOwner("DELETE FROM onboarding_path WHERE subject = '" + SUBJECT + "'");
        asOwner("DELETE FROM identity_user WHERE organization_id = '" + ORGANIZATION + "'");
    }

    @Test
    void anExportCarriesThePersonsOwnDataAndNothingRootedAtAnOrganization() throws IOException {

        // when
        var actual = mvc.get().uri("/axonia/me/data-export").with(signedInWithNoOrganization()).exchange();

        // then
        assertThat(actual).hasStatusOk();
        assertThat(actual.getResponse().getHeader("Content-Disposition"))
                .startsWith("attachment; filename=\"axonia-export-personal-");
        var entries = unzip(actual.getResponse().getContentAsByteArray());
        assertThat(entries.keySet()).map(name -> name.substring(name.indexOf('/') + 1))
                .containsExactlyInAnyOrder("onboarding.json", "index.html", "manifest.json");
        assertThat(entries).hasEntrySatisfying(entryNamed(entries, "onboarding.json"),
                onboarding -> assertThat(onboarding).contains(COMMUNITY_SLUG));
        assertThat(entries.values()).noneSatisfy(content -> assertThat(content).contains(SUBJECT))
                .noneSatisfy(content -> assertThat(content).contains(ORGANIZATION_PROFILE_NAME));
    }

    @Test
    void anErasureRemovesThePersonsOwnDataAndTheirIdentityProviderAccount() throws SQLException {

        // when
        var actual = mvc.delete().uri("/axonia/me/account").with(signedInWithNoOrganization()).exchange();

        // then
        assertThat(actual).hasStatus(204);
        assertThat(onboardingRowsLeft()).isZero();
        assertThat(countAsOwner("SELECT count(*) FROM identity_user WHERE organization_id = '" + ORGANIZATION + "'"))
                .as("a tenant's row is the tenant's erasure to make, not this one's").isOne();
        verify(identityAccounts).deleteAccount(SUBJECT);
    }

    @Test
    void anErasureWhoseIdentityProviderDeletionFailsSaysTheDataIsAlreadyGone() throws SQLException {

        // given
        doThrow(new IllegalStateException("identity provider down")).when(identityAccounts).deleteAccount(SUBJECT);

        // when
        var actual = mvc.delete().uri("/axonia/me/account").with(signedInWithNoOrganization()).exchange();

        // then
        assertThat(actual).hasStatus(502).bodyJson().extractingPath("$.code").isEqualTo("ACCOUNT_ERASURE_INCOMPLETE");
        assertThat(onboardingRowsLeft()).isZero();
    }

    private static long onboardingRowsLeft() throws SQLException {

        return countAsOwner("SELECT count(*) FROM onboarding_path WHERE subject = '" + SUBJECT + "'");
    }
}
