package dev.axonia.selfhosted;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import dev.axonia.identity.IdentityAccounts;
import dev.axonia.identity.MyOrganizationsService;
import dev.axonia.identity.OrganizationMembershipView;
import dev.axonia.privacy.ErasureBlocker;
import dev.axonia.privacy.ErasurePreflight;
import tools.jackson.databind.json.JsonMapper;

import static dev.axonia.selfhosted.SelfHostedTestInfrastructure.ORGANIZATION_ID;
import static dev.axonia.selfhosted.SelfHostedTestInfrastructure.ORGANIZATION_NAME;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one Organization of an installation, as the switcher reads it and as erasure meets it, against a token the
 * company's issuer signed. Each test owns its subject: the database is shared and never reset.
 */
@SelfHostedSpringBootTest
class SelfHostedMembershipTest {

    private static final OrganizationMembershipView THE_ONE_ORGANIZATION = new OrganizationMembershipView(
            ORGANIZATION_ID, ORGANIZATION_NAME, null, null, "ENTERPRISE");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MockOidcIssuer issuer;

    @Autowired
    private MyOrganizationsService myOrganizations;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ApplicationContext context;

    @Test
    void aSubjectSigningInForTheFirstTimeBelongsToTheOneOrganization() throws SQLException {

        // given
        assertThat(profiles("c1-first-timer")).isZero();

        // when
        var actual = mvc.get().uri("/axonia/me/organizations")
                .with(SelfHostedRequests.signedIn(tokenOf("c1-first-timer"))).exchange();

        // then
        assertThat(actual).hasStatusOk().bodyJson()
                .isEqualTo(jsonMapper.writeValueAsString(List.of(THE_ONE_ORGANIZATION)));
    }

    @Test
    void aSubjectWhoSignedInBeforeBelongsToTheOneOrganization() throws SQLException {

        // given
        var token = tokenOf("c1-returning");
        assertThat(mvc.get().uri("/axonia/me").with(SelfHostedRequests.signedIn(token)).exchange()).hasStatusOk();
        assertThat(profiles("c1-returning")).isOne();

        // when
        var actual = mvc.get().uri("/axonia/me/organizations").with(SelfHostedRequests.signedIn(token)).exchange();

        // then
        assertThat(actual).hasStatusOk().bodyJson()
                .isEqualTo(jsonMapper.writeValueAsString(List.of(THE_ONE_ORGANIZATION)));
    }

    /**
     * A person the directory pushed over SCIM, or erasure's preflight, asks without a request and without a profile.
     */
    @Test
    void aSubjectTheDirectoryKnowsButWhoNeverSignedInBelongsToTheOneOrganization() throws SQLException {

        // when
        var actual = myOrganizations.organizationsOf("c1-never-signed-in");

        // then
        assertThat(actual).containsExactly(THE_ONE_ORGANIZATION);
        assertThat(profiles("c1-never-signed-in")).isZero();
    }

    /** The company is the controller of a member's data; the account is its directory's, never axonia's to delete. */
    @Test
    void aMembersOwnErasureIsRefusedAndReachesNoIdentityProvider() throws SQLException {

        // given
        var token = tokenOf("c1-leaver");

        // when
        var actualPreflight = mvc.get().uri("/axonia/me/account/erasure-preflight")
                .with(SelfHostedRequests.signedIn(token)).exchange();
        var actualErasure = mvc.delete().uri("/axonia/me/account").with(SelfHostedRequests.signedIn(token)).exchange();

        // then
        assertThat(actualPreflight).hasStatusOk().bodyJson()
                .isEqualTo(jsonMapper.writeValueAsString(new ErasurePreflight(false, List.of(
                        new ErasureBlocker(ErasureBlocker.BlockerKind.ENTERPRISE_MEMBERSHIP, ORGANIZATION_ID, null)))));
        assertThat(actualErasure).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code")
                .isEqualTo("ACCOUNT_ERASURE_BLOCKED");

        assertThat(profiles("c1-leaver")).isOne();
        assertThat(context.getBean(IdentityAccounts.class).getClass().getName())
                .isEqualTo("dev.axonia.selfhosted.internal.DirectoryOwnedAccounts");
    }

    private String tokenOf(String subject) {
        return issuer.token(subject, Map.of("email", subject + "@acme.test", "name", subject));
    }

    private static long profiles(String subject) throws SQLException {
        return SelfHostedDatabase.countAsOwner("select count(*) from identity_user where user_id = '" + subject + "'");
    }
}
