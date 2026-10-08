package dev.axonia.privacy;

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
import dev.axonia.ExportScope;
import dev.axonia.founding.OrganizationFounder;
import dev.axonia.identity.IdentityAccounts;
import dev.axonia.identity.OrganizationMembershipSource;
import dev.axonia.identity.OrganizationMembershipSource.OrganizationMembership;

import static dev.axonia.privacy.ExportLeakFixtures.NEIGHBOUR;
import static dev.axonia.privacy.ExportLeakFixtures.NEIGHBOUR_EMAIL;
import static dev.axonia.privacy.ExportLeakFixtures.NEIGHBOUR_NAME;
import static dev.axonia.privacy.ExportLeakFixtures.NEIGHBOUR_SCIM_ID;
import static dev.axonia.privacy.ExportLeakFixtures.TARGET;
import static dev.axonia.privacy.ExportLeakFixtures.TARGET_EMAIL;
import static dev.axonia.privacy.ExportLeakFixtures.TARGET_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The export mirror of {@link ErasureSweepTest}, and its own layer: a member export contains no other member's subject,
 * name or email. It knows nothing about which columns are supposed to hold personal data - it assembles the real ZIP
 * the member would download, decompresses every entry, and looks for the neighbour in the bytes.
 *
 * <p>
 * That is what the per-module row-count assertions could not do. Each of those asks its own exporter whether it
 * returned the right rows; none of them looks at what is inside a row, and {@link internal.ExportPackageWriterTest}
 * only ever sees fake fragments. Between them, an exporter could hand over a colleague's subject on a row that is
 * genuinely the member's, or ship a credential digest, with every test in the module green - which is exactly what
 * happened.
 *
 * <p>
 * The fixture guard is per needle rather than a single non-empty check. The neighbour's own export must carry every one
 * of them, so a seeding INSERT that quietly stops running turns the assertion below from proof into vacuum - and this
 * catches that rather than passing green through it.
 *
 * <p>
 * Founded through open code with the same {@code @MockitoBean}s and {@code cleanup.sql} convention as
 * {@link ErasureSweepTest}, so the two share one Spring context; {@code CloudExportLeakTest} adds the closed rows.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class ExportLeakTest {

    @Autowired
    private OrganizationFounder founder;

    @Autowired
    private DataExport dataExport;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @MockitoBean
    private OrganizationMembershipSource memberships;

    @MockitoBean
    private IdentityAccounts identityAccounts;

    @MockitoBean
    private MemberDataRightsPolicy memberDataRights;

    private ExportLeakFixtures fixtures;

    /**
     * Per fixture rather than per class: this test commits its data and each test seeds its own Organization, and
     * {@code personal_access_token.token_hash} is globally unique.
     */
    private String patHash;

    @BeforeEach
    void fixtures() {
        fixtures = new ExportLeakFixtures(jdbc, dataExport);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aMemberExportCarriesNoOtherMembersSubjectNameOrEmail() {

        // given
        var organizationId = seedTwoMembers("leak-org");
        var neighbourNeedles = List.of(NEIGHBOUR_NAME, NEIGHBOUR_EMAIL);
        var neighboursOwnPackage = fixtures.packageFor(new ExportScope.OneMember(organizationId, NEIGHBOUR));
        assertThat(neighbourNeedles).allSatisfy(needle -> assertThat(neighboursOwnPackage)
                .describedAs("the fixture must plant '%s' before the target's package can prove anything", needle)
                .contains(needle));

        // when
        var targetsPackage = fixtures.packageFor(new ExportScope.OneMember(organizationId, TARGET));

        // then
        assertThat(targetsPackage)
                .describedAs("the member's own data must still be there - this withholds, it does not gut the package")
                .contains(TARGET_NAME, TARGET_EMAIL);
        assertThat(neighbourNeedles).allSatisfy(needle -> assertThat(targetsPackage)
                .describedAs("a member's export must carry no other member's identity").doesNotContain(needle));
    }

    /**
     * An Organization id, a subject and a SCIM id are all keys into axonia's own systems, and the one file guaranteed
     * to leave the building is the worst place for them.
     */
    @Test
    void noExportPackageCarriesATenantIdASubjectOrADirectoryIdentifier() {

        // given
        var organizationId = seedTwoMembers("key-org");

        // when
        var membersPackage = fixtures.packageFor(new ExportScope.OneMember(organizationId, TARGET));
        var organizationsPackage = fixtures.packageFor(new ExportScope.WholeOrganization(organizationId));

        // then
        assertThat(membersPackage).doesNotContain(organizationId, TARGET, NEIGHBOUR, NEIGHBOUR_SCIM_ID,
                "directory-id-1a04");
        assertThat(organizationsPackage).doesNotContain(organizationId, TARGET, NEIGHBOUR, NEIGHBOUR_SCIM_ID,
                "directory-id-1a04");
    }

    @Test
    void noExportPackageCarriesACredentialDigest() {

        // given
        var organizationId = seedTwoMembers("digest-org");

        // when
        var membersPackage = fixtures.packageFor(new ExportScope.OneMember(organizationId, TARGET));
        var organizationsPackage = fixtures.packageFor(new ExportScope.WholeOrganization(organizationId));

        // then
        assertThat(membersPackage).doesNotContain(patHash);
        assertThat(organizationsPackage).doesNotContain(patHash);
    }

    private String seedTwoMembers(String organizationId) {

        patHash = "leak-pat-digest-" + organizationId;
        when(memberDataRights.exercisedByMembers(organizationId)).thenReturn(Optional.of(true));
        new ErasureSweepFixtures(jdbc).found(founder, organizationId, "Leak Organization", List.of(TARGET, NEIGHBOUR));
        for (var member : List.of(TARGET, NEIGHBOUR)) {
            when(memberships.membershipsFor(member))
                    .thenReturn(List.of(new OrganizationMembership(organizationId, "Leak Organization")));
        }
        ErasureSweepFixtures.authenticateAsInOrg(TARGET, organizationId);
        fixtures.seedTwoMembers(organizationId, patHash);
        return organizationId;
    }
}
