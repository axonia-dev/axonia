package dev.axonia.identity.internal;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.ExportFragment;
import dev.axonia.ExportScope;
import dev.axonia.FootprintTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityPersonalDataExporterTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";
    private static final String TARGET_SCIM_ID = "target-scim-id";
    private static final String NEIGHBOUR_SCIM_ID = "neighbour-scim-id";
    private static final String TARGET_AVATAR_KEY = "avatars/default/target.jpg";
    private static final String NEIGHBOUR_AVATAR_KEY = "avatars/default/neighbour.jpg";
    private static final String TARGET_OVERRIDE_KEY = "avatars/default/target-own.png";
    private static final String GROUP_NAME = "Exporter Test Group";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private IdentityPersonalDataExporter exporter;

    @Test
    void memberScopedExportCarriesOnlyTheMembersOwnRowsAndTheirAvatarMediaKey() {

        seedEveryTableForBothMembers();

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        assertThat(fragment.fileName()).isEqualTo("identity.json");
        assertThat(rowsOf(fragment, "identity_user")).hasSize(1)
                .allSatisfy(row -> assertThat(row).containsEntry("user_id", TARGET));
        assertThat(rowsOf(fragment, "personal_access_token")).hasSize(1)
                .allSatisfy(row -> assertThat(row).containsEntry("user_id", TARGET));
        assertThat(rowsOf(fragment, "directory_group_member")).hasSize(1)
                .allSatisfy(row -> assertThat(row).containsEntry("user_id", TARGET));
        assertThat(fragment.mediaKeys()).containsExactly(TARGET_AVATAR_KEY);
    }

    @Test
    void organizationScopedExportCarriesEveryMembersRowsAndEveryAvatarMediaKey() {

        seedEveryTableForBothMembers();

        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        assertThat(rowsOf(fragment, "identity_user")).hasSize(2);
        assertThat(rowsOf(fragment, "personal_access_token")).hasSize(2);
        assertThat(rowsOf(fragment, "directory_group_member")).hasSize(2);
        assertThat(fragment.mediaKeys()).containsExactlyInAnyOrder(TARGET_AVATAR_KEY, NEIGHBOUR_AVATAR_KEY);
    }

    /**
     * Every table emits the facts its footprint names and nothing beside them, in either scope. The row keys are the
     * whole contract of the file a member downloads, so they are asserted exhaustively rather than sampled: a column
     * added to one of these tables next year reaches nobody's export until somebody decides it belongs there, and this
     * is what says so.
     */
    @Test
    void everyTableEmitsExactlyTheFactsItsFootprintNames() {

        seedEveryTableForBothMembers();

        assertEmitsExactly(exporter.export(new ExportScope.OneMember(ORG, TARGET)));
        assertEmitsExactly(exporter.export(new ExportScope.WholeOrganization(ORG)));
    }

    private static void assertEmitsExactly(ExportFragment fragment) {

        assertThat(rowsOf(fragment, "identity_user")).isNotEmpty()
                .allSatisfy(row -> assertThat(row).containsOnlyKeys("user_id", "display_name", "email",
                        "observed_time_zone", "declared_time_zone", "business_unit", "department", "team", "active",
                        "arrival", "last_seen_at", "created_at", "updated_at"));
        assertThat(rowsOf(fragment, "personal_access_token")).isNotEmpty()
                .allSatisfy(row -> assertThat(row).containsOnlyKeys("user_id", "name", "scopes", "last_used_at",
                        "revoked_at", "expires_at", "created_at", "updated_at"));
        assertThat(rowsOf(fragment, "directory_group_member")).isNotEmpty().allSatisfy(
                row -> assertThat(row).containsOnlyKeys("user_id", "created_at", "updated_at", "directory_group"));
    }

    /**
     * A key into a system is not a fact about a person. The SCIM and external ids are how a customer's directory and
     * identity provider file this member, and the two avatar keys are where object storage keeps their picture; all
     * five are still erased with the member, and none of them is printed. Asserting on the serialised fragment catches
     * the column name wherever it appears, including as a label an exporter invented.
     */
    @Test
    void noExportNamesADirectoryKeyOrAnObjectStorageKey() {

        seedEveryTableForBothMembers();

        var member = json(exporter.export(new ExportScope.OneMember(ORG, TARGET)));
        var organization = json(exporter.export(new ExportScope.WholeOrganization(ORG)));

        assertThat(member).doesNotContain("scim_id", "external_id", "avatar_media_key", "avatar_override_key",
                TARGET_SCIM_ID);
        assertThat(organization).doesNotContain("scim_id", "external_id", "avatar_media_key", "avatar_override_key",
                TARGET_SCIM_ID, NEIGHBOUR_SCIM_ID);
    }

    /**
     * The regression the withholding risks. The avatar's key left the projection, so anything reading it off the
     * exported rows now finds nothing - and the member's picture would quietly stop being bundled into the ZIP while
     * every other assertion here stayed green.
     */
    @Test
    void theAvatarIsStillBundledThoughItsKeyLeftTheProjection() {

        seedIdentityUser(TARGET, TARGET_SCIM_ID, TARGET_AVATAR_KEY);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        assertThat(json(fragment)).doesNotContain(TARGET_AVATAR_KEY);
        assertThat(fragment.mediaKeys()).contains(TARGET_AVATAR_KEY);
    }

    /** A raw group id means nothing to the person reading their own export. */
    @Test
    void membershipRowsCarryTheNameOfTheirGroup() {

        seedEveryTableForBothMembers();

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        assertThat(rowsOf(fragment, "directory_group_member")).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("directory_group", GROUP_NAME));
    }

    /**
     * The join is left, not inner. A directory group member whose {@code identity_user} row does not exist - a person
     * the directory syncs but who has never signed in - keeps its own facts and carries a null subject rather than
     * dropping out of the Organization's export entirely.
     */
    @Test
    void anUnprovisionedDirectoryMemberKeepsItsRowWithANullSubject() {

        seedDirectoryGroupMember(seedDirectoryGroup(), "never-provisioned-scim-id");

        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        assertThat(rowsOf(fragment, "directory_group_member")).singleElement().satisfies(row -> {
            assertThat(row).containsKey("user_id").containsEntry("directory_group", GROUP_NAME);
            assertThat(row.get("user_id")).isNull();
        });
    }

    /**
     * The thing a {@code SELECT *} would have shipped that nothing here could see. The digest is not exploitable today
     * - the secret behind it is 256-bit and SHA-256'd - but putting it in a file a member downloads turns any future
     * weakening of the hasher into a breach rather than a refactor.
     */
    @Test
    void noExportCarriesACredentialDigest() {

        seedIdentityUser(TARGET, TARGET_SCIM_ID, TARGET_AVATAR_KEY);
        seedPersonalAccessToken(TARGET);

        var member = json(exporter.export(new ExportScope.OneMember(ORG, TARGET)));
        var organization = json(exporter.export(new ExportScope.WholeOrganization(ORG)));

        assertThat(member).doesNotContain(patHashOf(TARGET));
        assertThat(organization).doesNotContain(patHashOf(TARGET));
        // the member's own side of the same row is still there - this withholds, it does not gut the fragment
        assertThat(member).contains(TARGET);
    }

    @Test
    void exportCarriesTheMembersAgentConnectionWithoutItsPkceOrRedirectDetail() {

        seedAgentConnection(TARGET);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        assertThat(rowsOf(fragment, "agent_connection")).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("client_name", "Claude Code").doesNotContainKeys("code_challenge", "redirect_uri"));
    }

    @Test
    void exportBundlesBothTheMirroredAvatarAndTheOverride() {

        seedIdentityUserWithOverride(TARGET, TARGET_SCIM_ID, TARGET_AVATAR_KEY, TARGET_OVERRIDE_KEY);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        assertThat(fragment.mediaKeys()).containsExactlyInAnyOrder(TARGET_AVATAR_KEY, TARGET_OVERRIDE_KEY);
    }

    /** Every identity table is rooted at an Organization, so a person's own export has no identity file at all. */
    @Test
    void aPersonsExportHoldsNothing() {

        seedEveryTableForBothMembers();

        var fragment = exporter.export(new ExportScope.Person(TARGET));

        assertThat(fragment.fileName()).isEqualTo("identity.json");
        assertThat(fragment.holdsNothing()).isTrue();
    }

    private void seedEveryTableForBothMembers() {

        var groupId = seedDirectoryGroup();
        seedIdentityUser(TARGET, TARGET_SCIM_ID, TARGET_AVATAR_KEY);
        seedIdentityUser(NEIGHBOUR, NEIGHBOUR_SCIM_ID, NEIGHBOUR_AVATAR_KEY);
        seedPersonalAccessToken(TARGET);
        seedPersonalAccessToken(NEIGHBOUR);
        seedDirectoryGroupMember(groupId, TARGET_SCIM_ID);
        seedDirectoryGroupMember(groupId, NEIGHBOUR_SCIM_ID);
    }

    private Long seedDirectoryGroup() {

        return jdbc.queryForObject(
                "INSERT INTO directory_group (organization_id, scim_id, external_id, display_name, "
                        + "reconciliation_key, created_at, updated_at, version) VALUES (:org, "
                        + "'exporter-test-group-scim-id', 'exporter-test-group-external-id', :name, "
                        + "'exporter-test-reconciliation-key', now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "name", GROUP_NAME), Long.class);
    }

    private void seedIdentityUser(String userId, String scimId, String avatarMediaKey) {

        jdbc.update("INSERT INTO identity_user (organization_id, user_id, display_name, scim_id, arrival, "
                + "avatar_media_key, created_at, updated_at, version) VALUES (:org, :user, :user, :scimId, 'SYNCED', "
                + ":avatarKey, now(), now(), 0)",
                Map.of("org", ORG, "user", userId, "scimId", scimId, "avatarKey", avatarMediaKey));
    }

    private void seedIdentityUserWithOverride(String userId, String scimId, String avatarMediaKey,
            String avatarOverrideKey) {

        jdbc.update("INSERT INTO identity_user (organization_id, user_id, display_name, scim_id, arrival, "
                + "avatar_media_key, avatar_override_key, created_at, updated_at, version) VALUES (:org, :user, "
                + ":user, :scimId, 'SYNCED', :avatarKey, :overrideKey, now(), now(), 0)",
                Map.of("org", ORG, "user", userId, "scimId", scimId, "avatarKey", avatarMediaKey, "overrideKey",
                        avatarOverrideKey));
    }

    private void seedAgentConnection(String userId) {

        jdbc.update("INSERT INTO agent_connection (organization_id, user_id, client_id, client_name, client_host, "
                + "scopes, resource, redirect_uri, code_challenge, activated_at, ends_at, created_at, updated_at, "
                + "version) VALUES (:org, :user, 'https://claude.ai/cc', 'Claude Code', 'claude.ai', 'read', "
                + "'https://axonia.test/mcp', 'http://localhost/callback', 'exporter-test-challenge', now(), "
                + "now() + interval '1 day', now(), now(), 0)", Map.of("org", ORG, "user", userId));
    }

    private void seedPersonalAccessToken(String userId) {

        jdbc.update("INSERT INTO personal_access_token (organization_id, user_id, token_hash, name, scopes, "
                + "created_at, updated_at, version) VALUES (:org, :user, :hash, 'Exporter Test PAT', 'read', now(), "
                + "now(), 0)", Map.of("org", ORG, "user", userId, "hash", patHashOf(userId)));
    }

    private static String patHashOf(String userId) {
        return "exporter-test-pat-hash-" + userId;
    }

    private void seedDirectoryGroupMember(Long groupId, String userScimId) {

        jdbc.update(
                "INSERT INTO directory_group_member (organization_id, group_id, user_scim_id, created_at, "
                        + "updated_at, version) VALUES (:org, :groupId, :scimId, now(), now(), 0)",
                Map.of("org", ORG, "groupId", groupId, "scimId", userScimId));
    }

}
