package dev.axonia.privacy.application;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import dev.axonia.CoreSpringBootTest;
import dev.axonia.founding.api.OrganizationFounder;
import dev.axonia.identity.api.spi.IdentityAccounts;
import dev.axonia.identity.api.spi.OrganizationMembershipSource;
import dev.axonia.identity.api.spi.OrganizationMembershipSource.OrganizationMembership;
import dev.axonia.privacy.api.spi.MemberDataRightsPolicy;

import static dev.axonia.privacy.application.ErasureSweepFixtures.authenticateAsInOrg;
import static dev.axonia.privacy.application.ErasureSweepFixtures.scimIdOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Layer 4 of the gate, and the only one of the four that needs no manifest to be correct: it does not know which
 * columns are supposed to hold personal data, only that after an erasure nothing in the whole public schema may still
 * name the erased subject, their display name, or their email. A column classified perfectly in
 * {@code *PersonalDataFootprint} but whose eraser SQL quietly misses a row still fails here, which is what makes this
 * test the backstop for the other three rather than a fourth restatement of the same manifest.
 *
 * <p>
 * Deliberately NOT the {@code axonia_app}/RLS stack {@link dev.axonia.RlsStackIsolationTest} and
 * {@code MemberErasureIntegrationTest} use. Those tests exist to prove {@code OrganizationScope.runAs} genuinely gates
 * access; this one is not testing RLS at all; it is looking for leftover rows, and every one of those tests' own
 * fixtures shows the erasers already filter by an explicit {@code organization_id = :org} predicate rather than leaning
 * on the database to filter for them. Connecting as the shared Testcontainers superuser - the plain
 * {@code @SpringBootTest @ActiveProfiles("DEV")} setup every other non-RLS integration test in this module already uses
 * - sees every row regardless of tenant with no scope dance required, which is exactly what a schema-wide sweep needs.
 * The tradeoff of that choice: an eraser whose {@code OrganizationScope.runAs} wrap went inert would still erase
 * everything under a superuser connection (nothing here is RLS-checked), so this class alone would stay green through
 * that regression. {@code MemberErasureIntegrationTest} is what actually covers an inert wrap, by running under
 * {@code axonia_app}; this layer is not a substitute for it.
 *
 * <p>
 * The Organization is founded through open code ({@link OrganizationFounder}), so it is an Enterprise one, whose
 * members' data rights are exercised by the Organization. {@link MemberDataRightsPolicy} is therefore stubbed to say
 * the members exercise them here, which is the one answer the self-serve erasure path needs; the sweep itself is what
 * this class tests. The context shares {@link ExportLeakTest}'s, which declares the same {@code @MockitoBean}s, and
 * connects to the one shared {@code jdbc:tc} daemon container like every other DEV-profile {@code @SpringBootTest}.
 *
 * <p>
 * The fixture seeds one row per module's eraser - including every branch of
 * {@link dev.axonia.events.adapter.out.privacy.EventsMemberDataEraser}'s time-based and outcome-based splits - so a
 * single missed {@code UPDATE} or {@code DELETE} anywhere in the open erasers surfaces here by name;
 * {@code CloudErasureSweepTest} adds the closed ones. The one exception is
 * {@code dev.axonia.access.adapter.out.privacy.AccessMemberDataEraser}: {@code MemberPurge} runs
 * {@code leaveForErasure} before any eraser, and that path already drops the subject's {@code role_binding} row, so the
 * access eraser's own DELETE always affects zero rows in this fixture - not a coverage hole (the column is still
 * exercised, twice over, by that leave path and by {@code AccessMemberDataEraserTest}), just not proof of that one
 * eraser's SQL specifically.
 *
 * <p>
 * Matching is by substring, not equality, so an identifier denormalised into a longer value is visible: the fixture's
 * {@code identity_user.avatar_media_key} is an object key built around the subject, and its
 * {@code personal_access_token.token_hash} is prefixed, and neither could ever have matched {@code = :needle}. The
 * free-text columns ({@code artifact.search_document}, {@code community_session.notes}, {@code topic.description}) are
 * seeded with real prose rather than {@code ''}, but deliberately name nobody: no eraser scrubs member-authored free
 * text, and none is designed to. {@code search_document} is derived from an artifact's own title, description and tags
 * ({@code ArtifactService#searchDocument}) and never from a contributor, and {@code notes}/{@code description} are
 * community content declared {@code notPersonal}. Seeding a member's name inside one of those and demanding it be gone
 * would assert a behaviour this feature does not have and could not implement reliably.
 *
 * <p>
 * The sweep's blanket claim - every trace of an erased member gone from every text column - has one declared exception
 * this fixture never exercises: {@code organization.closed_by} is {@code SUBJECT}/{@code RETAIN} (closing an org
 * deliberately keeps a record of who closed it). This fixture never has the target close an organization, so that
 * exception stays out of scope here; a future fixture that does must special-case that column, or a policy-correct
 * {@code RETAIN} will read as a red build.
 *
 * <p>
 * It commits real data (no {@code @Transactional} rollback), so the class truncates after itself via
 * {@code cleanup.sql}, the same convention {@code EnterpriseProvisioningIntegrationTest} follows.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
public class ErasureSweepTest {

    private static final String TARGET = "sweep-target";
    private static final String NAME = "Erasure Target";
    private static final String EMAIL = "erasure-target@example.test";

    /**
     * A second subject for the cross-organization test, deliberately sharing no substring with {@link #TARGET}: the
     * sweep matches by substring, and two overlapping ids would let one test's committed leftovers register as the
     * other's hits.
     */
    private static final String CROSS_ORG_SUBJECT = "cross-org-subject";

    @Autowired
    private OrganizationFounder founder;

    @Autowired
    private AccountErasure accountErasure;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @MockitoBean
    private OrganizationMembershipSource memberships;

    @MockitoBean
    private IdentityAccounts identityAccounts;

    @MockitoBean
    private MemberDataRightsPolicy memberDataRights;

    private ErasureSweepFixtures fixtures;

    @BeforeEach
    void fixtures() {
        fixtures = new ErasureSweepFixtures(jdbc);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void afterErasureTheSubjectNameAndEmailSurviveNowhereInTheSchema() {

        // given
        var organizationId = foundWithMember("sweep-org", TARGET);
        fixtures.seedOpenRows(organizationId, TARGET, NAME, EMAIL);
        // The scim id is its own needle: 'scim-sweep-target' equals none of the subject, name or email, so without it
        // the predicate could never see directory_group_member.user_scim_id - a declared SUBJECT column - go missing.
        var needles = List.of(TARGET, NAME, EMAIL, scimIdOf(TARGET));
        // Per needle rather than merely non-empty, so a seeding INSERT that stops running cannot pass vacuously.
        var before = fixtures.hitsFor(needles);
        assertThat(needles).allSatisfy(needle -> assertThat(before)
                .describedAs("the fixture must plant '%s' before erasure can prove anything about it", needle)
                .anyMatch(hit -> hit.endsWith("'" + needle + "'")));

        // when
        accountErasure.erase(TARGET);

        // then
        assertThat(fixtures.hitsFor(needles))
                .describedAs("every trace of an erased member must be gone from every " + "text column").isEmpty();
    }

    /**
     * A subject is the same {@code sub} in every Organization it belongs to, so an eraser that lost its
     * {@code organization_id = :org} predicate would reach the same person's rows in Organizations this erasure was
     * never asked about. RLS protects production from that; this class connects as the Testcontainers superuser
     * precisely so it does not, which makes it the one place a dropped predicate can surface at all.
     */
    @Test
    void erasureInOneOrganizationLeavesTheSameSubjectsRowsInAnotherUntouched() {

        // given
        var erased = foundWithMember("cross-org-erased", CROSS_ORG_SUBJECT);
        fixtures.seedOpenRows(erased, CROSS_ORG_SUBJECT, "Cross Org Subject", "cross-org@example.test");
        var untouched = found("cross-org-untouched", List.of());
        fixtures.seedArtifactNaming(untouched, CROSS_ORG_SUBJECT);
        authenticateAsInOrg(CROSS_ORG_SUBJECT, erased);

        // when
        accountErasure.erase(CROSS_ORG_SUBJECT);

        // then
        assertThat(fixtures.contributorsIn(erased))
                .describedAs("the erased Organization's rows must no longer name " + "the subject")
                .doesNotContain(CROSS_ORG_SUBJECT);
        assertThat(fixtures.contributorsIn(untouched)).describedAs(
                "another Organization's rows name the same " + "subject and this erasure was never asked about them")
                .containsOnly(CROSS_ORG_SUBJECT);
    }

    private String foundWithMember(String organizationId, String userId) {

        found(organizationId, List.of(userId));
        when(memberships.membershipsFor(userId))
                .thenReturn(List.of(new OrganizationMembership(organizationId, "Sweep Organization")));
        // an org-scoped token keeps the dev fallback's per-connection warning out of the sweep
        authenticateAsInOrg(userId, organizationId);
        return organizationId;
    }

    private String found(String organizationId, List<String> members) {

        when(memberDataRights.exercisedByMembers(organizationId)).thenReturn(Optional.of(true));
        fixtures.found(founder, organizationId, "Sweep Organization", members);
        return organizationId;
    }
}
