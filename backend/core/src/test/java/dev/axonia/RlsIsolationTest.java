package dev.axonia;

import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Row-level security isolation of the open tables, over raw JDBC as the runtime role. Each probe names its table with
 * {@link IsolationProbe}; every RLS-enabled table of the open schema is probed here or awaiting a probe;
 * {@code CloudRlsIsolationTest} holds the Cloud's schema to the same.
 */
class RlsIsolationTest {

    // Open tables awaiting an isolation test; this list may only shrink.
    static final Set<String> NOT_PROBED = Set.of("action_item", "action_item_checkpoint", "agent_tool_call", "artifact",
            "artifact_attachment", "artifact_type", "bookmark", "communication_expired_message",
            "community_chat_destination", "community_kind", "community_session", "contribution_love",
            "directory_group_member", "event_section_type", "event_series", "expertise_dismissed_signal",
            "expertise_hidden_tag", "expertise_opt_out", "expertise_signal", "expertise_tag_alias",
            "expertise_tag_dismissal", "memberships", "recognized_contribution", "role", "role_binding", "scim_token",
            "session_attendance", "topic", "topic_vote");

    // Lower this when a table leaves NOT_PROBED for a probe; never raise it.
    static final int NOT_PROBED_MAX = 29;

    static final String NOT_PROBED_MAX_MISMATCH = "NOT_PROBED_MAX must match NOT_PROBED: lower it when a table leaves "
            + "the list, and probe a new table instead of listing it";

    private static final RlsProbeDatabase DB = new RlsProbeDatabase(MigrationChain.SELF_HOSTED);

    @BeforeAll
    static void start() {
        DB.start();
    }

    @AfterAll
    static void stop() {
        DB.stop();
    }

    private java.sql.Connection asApp() throws Exception {
        return DB.asApp();
    }

    @Test
    void theOpenTablesAwaitingAProbeHaveRowLevelSecurityAndNoProbe() throws Exception {

        // given
        var live = DB.rlsEnabledTables();

        // when
        var probed = IsolationProbes.byTable().keySet();

        // then
        assertThat(NOT_PROBED).as("tables awaiting a probe that no longer have RLS - drop them").isSubsetOf(live);
        assertThat(NOT_PROBED).as("tables awaiting a probe that have one - drop them and lower NOT_PROBED_MAX")
                .doesNotContainAnyElementsOf(probed);
        assertThat(NOT_PROBED).as(NOT_PROBED_MAX_MISMATCH).hasSize(NOT_PROBED_MAX);
    }

    @Test
    void everyIsolationProbeBesideThisTestRuns() {

        IsolationProbes.assertEveryProbeBesideRuns(getClass());
    }

    @Test
    void everyRlsEnabledOpenTableIsProbedOrAwaitingAProbe() throws Exception {

        // given
        var probed = IsolationProbes.byTable().keySet();

        // when
        var accounted = new TreeSet<>(probed);
        accounted.addAll(NOT_PROBED);

        // then
        assertThat(DB.rlsEnabledTables()).as("the open schema's RLS-enabled tables, against the ones probed beside "
                + "this test or awaiting a probe: probe a new table with @IsolationProbe").isEqualTo(accounted);
    }

    @Test
    @IsolationProbe("identity_user")
    void unsetTenantSeesNoRows() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given
            s.execute("RESET app.current_org");

            // when
            try (var rs = s.executeQuery("SELECT count(*) FROM identity_user")) {
                rs.next();

                // then
                assertThat(rs.getInt(1)).isZero(); // fail-closed
            }
        }
    }

    @Test
    @IsolationProbe("identity_user")
    void tenantSeesOnlyItsOwnRows() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given
            s.execute("SET app.current_org = 'default'");
            s.execute(
                    "INSERT INTO identity_user (organization_id, user_id, display_name, scim_id, arrival, created_at, updated_at, version)"
                            + " VALUES ('default','u-rls-2','Dee','scim-u-rls-2','SYNCED', now(), now(), 0)");
            s.execute("SET app.current_org = 'acme'");

            // when
            try (var rs = s.executeQuery("SELECT count(*) FROM identity_user WHERE user_id = 'u-rls-2'")) {
                rs.next();

                // then
                assertThat(rs.getInt(1)).isZero(); // acme cannot see default's row
            }
        }
    }

    @Test
    @IsolationProbe("identity_user")
    void cannotInsertIntoAnotherTenant() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given
            s.execute("SET app.current_org = 'acme'");

            // then
            assertThatThrownBy(() -> s.execute(
                    "INSERT INTO identity_user (organization_id, user_id, display_name, scim_id, arrival, created_at, updated_at, version)"
                            + " VALUES ('default','sneaky-t5','X','scim-sneaky-t5','SYNCED', now(), now(), 0)"))
                    .hasMessageContaining("row-level security");
        }
    }

    @Test
    @IsolationProbe("identity_user")
    void cannotUpdateAcrossTenant() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given
            s.execute("SET app.current_org = 'default'");
            s.execute(
                    "INSERT INTO identity_user (organization_id, user_id, display_name, scim_id, arrival, created_at, updated_at, version)"
                            + " VALUES ('default','victim-t5','V','scim-victim-t5','SYNCED', now(), now(), 0)");
            s.execute("SET app.current_org = 'acme'");

            // when
            int updated = s.executeUpdate("UPDATE identity_user SET display_name='hacked' WHERE user_id='victim-t5'");

            // then
            assertThat(updated).isZero(); // acme cannot see or update default's row
        }
    }

    @Test
    @IsolationProbe("organization")
    void organizationIsTenantIsolated() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given: 'rls-probe' exists, seeded as owner before all, but belongs to no visible org scope
            s.execute("SET app.current_org = 'acme'");

            // when
            try (var rs = s.executeQuery("SELECT count(*) FROM organization WHERE id = 'rls-probe'")) {
                rs.next();

                // then
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    @Test
    @IsolationProbe("directory_group")
    void scimDirectoryGroupIsOrganizationIsolated() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given a group provisioned under 'default'
            s.execute("SET app.current_org = 'default'");
            s.execute("INSERT INTO directory_group (organization_id, scim_id, display_name, reconciliation_key,"
                    + " created_at, updated_at, version)"
                    + " VALUES ('default','g-iso-1','Eng','Eng', now(), now(), 0)");
            // when another Organization looks
            s.execute("SET app.current_org = 'acme'");
            try (var rs = s.executeQuery("SELECT count(*) FROM directory_group WHERE scim_id='g-iso-1'")) {
                rs.next();
                // then it sees nothing
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    @Test
    @IsolationProbe("reaction_definition")
    void reactionDefinitionIsOrganizationIsolated() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given a reaction definition proposed under 'default'
            s.execute("SET app.current_org = 'default'");
            s.execute("INSERT INTO reaction_definition (organization_id, name, image_key, state, created_at,"
                    + " updated_at) VALUES ('default','clapping-t5','img/clapping-t5.png', 'PENDING', now(), now())");
            s.execute("SET app.current_org = 'acme'");

            // when
            try (var rs = s.executeQuery("SELECT count(*) FROM reaction_definition WHERE name = 'clapping-t5'")) {
                rs.next();

                // then
                assertThat(rs.getInt(1)).isZero(); // acme cannot see default's definition
            }
        }
    }

    @Test
    @IsolationProbe("expertise_declaration")
    void expertiseDeclarationIsOrganizationIsolated() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given a declaration made under 'default'
            s.execute("SET app.current_org = 'default'");
            s.execute("INSERT INTO expertise_declaration (organization_id, community_id, user_id, tag, declared_at)"
                    + " VALUES ('default', 1, 'rls-declarer', 'rls-kafka', now())");
            s.execute("SET app.current_org = 'acme'");

            // when
            try (var rs = s.executeQuery("SELECT count(*) FROM expertise_declaration WHERE tag = 'rls-kafka'")) {
                rs.next();

                // then
                assertThat(rs.getInt(1)).isZero(); // acme cannot see default's declaration
            }
            // and acme cannot delete default's declaration
            assertThat(s.executeUpdate("DELETE FROM expertise_declaration WHERE tag = 'rls-kafka'")).isZero();
            // and acme cannot insert one under default's name (WITH CHECK denies it)
            assertThatThrownBy(() -> s.execute(
                    "INSERT INTO expertise_declaration (organization_id, community_id, user_id, tag, declared_at)"
                            + " VALUES ('default', 1, 'rls-sneaky', 'rls-kafka', now())"))
                    .hasMessageContaining("row-level security");
        }
    }

    @Test
    @IsolationProbe("learning_intent")
    void learningIntentIsOrganizationIsolated() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given an intent stated under 'default'
            s.execute("SET app.current_org = 'default'");
            s.execute("INSERT INTO learning_intent (organization_id, community_id, user_id, tag, declared_at)"
                    + " VALUES ('default', 1, 'rls-learner', 'rls-learn', now())");
            s.execute("SET app.current_org = 'acme'");

            // when
            try (var rs = s.executeQuery("SELECT count(*) FROM learning_intent WHERE tag = 'rls-learn'")) {
                rs.next();

                // then
                assertThat(rs.getInt(1)).isZero(); // acme cannot see default's intent
            }
            // and acme cannot delete default's intent
            assertThat(s.executeUpdate("DELETE FROM learning_intent WHERE tag = 'rls-learn'")).isZero();
            // and acme cannot insert one under default's name (WITH CHECK denies it)
            assertThatThrownBy(() -> s
                    .execute("INSERT INTO learning_intent (organization_id, community_id, user_id, tag, declared_at)"
                            + " VALUES ('default', 1, 'rls-sneaky', 'rls-learn', now())"))
                    .hasMessageContaining("row-level security");
        }
    }

    @Test
    @IsolationProbe("expertise_named_area")
    void expertiseNamedAreaIsOrganizationIsolated() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given a named area made under 'default'
            s.execute("SET app.current_org = 'default'");
            s.execute("INSERT INTO expertise_named_area (organization_id, community_id, name)"
                    + " VALUES ('default', 1, 'rls-messaging')");
            s.execute("SET app.current_org = 'acme'");

            // when
            try (var rs = s.executeQuery("SELECT count(*) FROM expertise_named_area WHERE name = 'rls-messaging'")) {
                rs.next();

                // then
                assertThat(rs.getInt(1)).isZero(); // acme cannot see default's named area
            }
            // and acme cannot rename or delete default's named area
            assertThat(
                    s.executeUpdate("UPDATE expertise_named_area SET name = 'rls-hacked' WHERE name = 'rls-messaging'"))
                    .isZero();
            assertThat(s.executeUpdate("DELETE FROM expertise_named_area WHERE name = 'rls-messaging'")).isZero();
            // and acme cannot insert one under default's name (WITH CHECK denies it)
            assertThatThrownBy(() -> s.execute("INSERT INTO expertise_named_area (organization_id, community_id, name)"
                    + " VALUES ('default', 1, 'rls-sneaky')")).hasMessageContaining("row-level security");
        }
    }

    @Test
    @IsolationProbe("expertise_named_area_tag")
    void expertiseNamedAreaTagIsOrganizationIsolated() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given a tag placed in a named area under 'default'
            s.execute("SET app.current_org = 'default'");
            long namedAreaId;
            try (var rs = s.executeQuery("INSERT INTO expertise_named_area (organization_id, community_id, name)"
                    + " VALUES ('default', 1, 'rls-streaming') RETURNING id")) {
                rs.next();
                namedAreaId = rs.getLong(1);
            }
            s.execute(
                    "INSERT INTO expertise_named_area_tag (organization_id, community_id, named_area_id, canonical_tag)"
                            + " VALUES ('default', 1, " + namedAreaId + ", 'rls-kafka')");
            s.execute("SET app.current_org = 'acme'");

            // when
            try (var rs = s
                    .executeQuery("SELECT count(*) FROM expertise_named_area_tag WHERE canonical_tag = 'rls-kafka'")) {
                rs.next();

                // then
                assertThat(rs.getInt(1)).isZero(); // acme cannot see default's placement
            }
            // and acme cannot move or delete default's placement
            assertThat(s.executeUpdate(
                    "UPDATE expertise_named_area_tag SET canonical_tag = 'rls-hacked' WHERE canonical_tag = 'rls-kafka'"))
                    .isZero();
            assertThat(s.executeUpdate("DELETE FROM expertise_named_area_tag WHERE canonical_tag = 'rls-kafka'"))
                    .isZero();
            // and acme cannot place a tag under default's name (WITH CHECK denies it)
            assertThatThrownBy(() -> s.execute(
                    "INSERT INTO expertise_named_area_tag (organization_id, community_id, named_area_id, canonical_tag)"
                            + " VALUES ('default', 1, " + namedAreaId + ", 'rls-sneaky')"))
                    .hasMessageContaining("row-level security");
        }
    }

    @Test
    @IsolationProbe("contribution_reaction")
    void contributionReactionIsOrganizationIsolated() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given a placement made under 'default'
            s.execute("SET app.current_org = 'default'");
            s.execute("INSERT INTO contribution_reaction (organization_id, community_id, contribution_type,"
                    + " contribution_id, reactor_user_id, emoji, created_at, updated_at)"
                    + " VALUES ('default', 1, 'SLOT_PRESENTED', 1, 'u-rls-react-t5', '🧠', now(), now())");
            s.execute("SET app.current_org = 'acme'");

            // when
            try (var rs = s.executeQuery(
                    "SELECT count(*) FROM contribution_reaction WHERE reactor_user_id = 'u-rls-react-t5'")) {
                rs.next();

                // then
                assertThat(rs.getInt(1)).isZero(); // acme cannot see default's placement
            }
        }
    }

    @Test
    @IsolationProbe("organization")
    void enterpriseOrgStaysInvisibleWithNoTenantSet() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given: 'rls-probe' is ENTERPRISE/PRIVATE and exists, and no org scope is set
            s.execute("RESET app.current_org");

            // when
            try (var rs = s.executeQuery("SELECT count(*) FROM organization WHERE id = 'rls-probe'")) {
                rs.next();

                // then
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    @Test
    @IsolationProbe("communities")
    void communityIsOrganizationIsolated() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given - a community of 'rls-comm-org', written under its own scope
            s.execute("SET app.current_org = 'rls-comm-org'");
            s.execute("INSERT INTO organization (id, name, kind, created_at, updated_at)"
                    + " VALUES ('rls-comm-org','Community Org','ENTERPRISE', now(), now())");
            s.execute("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version)"
                    + " VALUES ('rls-comm-org','Guild', now(), now(), 0)");
            var seeded = s.executeUpdate("INSERT INTO communities (organization_id, slug, name, description, status,"
                    + " kind_id, created_at, updated_at, version)"
                    + " SELECT 'rls-comm-org','rls-isolated','Rls Isolated','', 'ACTIVE', k.id, now(), now(), 0"
                    + " FROM community_kind k WHERE k.organization_id = 'rls-comm-org' AND k.label = 'Guild'");

            // when - read with no scope, then under another Organization's
            s.execute("RESET app.current_org");
            var unscoped = countOf(s, "SELECT count(*) FROM communities WHERE slug = 'rls-isolated'");
            s.execute("SET app.current_org = 'acme'");
            var otherScope = countOf(s, "SELECT count(*) FROM communities WHERE slug = 'rls-isolated'");
            var claimed = s.executeUpdate("UPDATE communities SET name = 'hacked' WHERE slug = 'rls-isolated'");

            // then
            assertThat(seeded).as("the community the probe hides").isOne();
            assertThat(unscoped).isZero();
            assertThat(otherScope).isZero();
            assertThat(claimed).isZero();
        }
    }

    private static int countOf(java.sql.Statement s, String query) throws Exception {
        try (var rs = s.executeQuery(query)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    @IsolationProbe("personal_access_token")
    void personalAccessTokenAuthLookupVisibleWithNoOrgSet() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given a token minted under 'default'
            s.execute("SET app.current_org = 'default'");
            s.execute("INSERT INTO personal_access_token (organization_id, user_id, token_hash, name, scopes,"
                    + " created_at, updated_at, version)"
                    + " VALUES ('default','u-pat','hash-pat-1','cli','read', now(), now(), 0)");
            s.execute("RESET app.current_org");

            // when the pre-auth bearer lookup runs with no org context
            try (var rs = s
                    .executeQuery("SELECT count(*) FROM personal_access_token WHERE token_hash = 'hash-pat-1'")) {
                rs.next();

                // then the row is visible (auth_lookup policy is USING(true)), so authenticate() can resolve it
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    @IsolationProbe("personal_access_token")
    void personalAccessTokenWriteIsOrganizationIsolated() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given the caller is scoped to 'acme'
            s.execute("SET app.current_org = 'acme'");

            // then it cannot mint a token owned by another Organization (WITH CHECK denies it)
            assertThatThrownBy(() -> s
                    .execute("INSERT INTO personal_access_token (organization_id, user_id, token_hash, name, scopes,"
                            + " created_at, updated_at, version)"
                            + " VALUES ('default','u-sneaky','hash-sneaky','x','read', now(), now(), 0)"))
                    .hasMessageContaining("row-level security");
        }
    }

    @Test
    @IsolationProbe("agent_connection_token")
    void agentConnectionTokenAuthLookupVisibleWithNoOrgSet() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given an access token issued under 'default'
            s.execute("SET app.current_org = 'default'");
            var connectionId = insertAgentConnection(s, "default", "u-axc-lookup");
            insertAgentConnectionToken(s, "default", connectionId, "hash-axa-lookup");
            s.execute("RESET app.current_org");

            // when the token endpoint or the /mcp filter looks it up before any organization is known
            try (var rs = s
                    .executeQuery("SELECT count(*) FROM agent_connection_token WHERE token_hash = 'hash-axa-lookup'")) {
                rs.next();

                // then the row is visible (auth_lookup policy is USING(true)), so the adapter can resolve it
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    @IsolationProbe("agent_connection_token")
    void agentConnectionTokenWriteIsOrganizationIsolated() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given a connection of 'default', so only the policy can refuse what follows
            s.execute("SET app.current_org = 'default'");
            var connectionId = insertAgentConnection(s, "default", "u-axc-write");
            s.execute("SET app.current_org = 'acme'");

            // then 'acme' cannot issue a token owned by another Organization (WITH CHECK denies it)
            assertThatThrownBy(() -> insertAgentConnectionToken(s, "default", connectionId, "hash-axa-sneaky"))
                    .hasMessageContaining("row-level security");
        }
    }

    @Test
    @IsolationProbe("agent_connection_token")
    void agentConnectionTokenCannotBeConsumedOrDeletedAcrossOrganizations() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given a token of 'default'
            s.execute("SET app.current_org = 'default'");
            var connectionId = insertAgentConnection(s, "default", "u-axc-consume");
            insertAgentConnectionToken(s, "default", connectionId, "hash-axa-victim");
            s.execute("SET app.current_org = 'acme'");

            // when 'acme', which can find it by hash, tries to consume or delete it
            var consumed = s.executeUpdate(
                    "UPDATE agent_connection_token SET consumed_at = now() WHERE token_hash = 'hash-axa-victim'");
            var deleted = s.executeUpdate("DELETE FROM agent_connection_token WHERE token_hash = 'hash-axa-victim'");

            // then the lookup policy lends it no write: nothing changed
            assertThat(consumed).isZero();
            assertThat(deleted).isZero();
        }
    }

    @Test
    @IsolationProbe("agent_connection")
    void agentConnectionIsOrganizationIsolated() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given a connection of 'default'
            s.execute("SET app.current_org = 'default'");
            insertAgentConnection(s, "default", "u-axc-isolated");
            s.execute("SET app.current_org = 'acme'");

            // when
            try (var rs = s.executeQuery("SELECT count(*) FROM agent_connection WHERE organization_id = 'default'")) {
                rs.next();

                // then 'acme' cannot see it
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    private static long insertAgentConnection(java.sql.Statement s, String organizationId, String userId)
            throws Exception {
        try (var rs = s.executeQuery("INSERT INTO agent_connection (organization_id, user_id, client_id, client_name,"
                + " client_host, scopes, resource, redirect_uri, code_challenge, activated_at, ends_at, created_at,"
                + " updated_at, version) VALUES ('" + organizationId + "','" + userId + "',"
                + " 'https://claude.ai/oauth/claude-code-client-metadata','Claude Code','claude.ai','read',"
                + " 'https://dev-local.axonia.example/mcp','http://localhost/callback',"
                + " 'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM', now(), now() + interval '180 days', now(), now(), 0)"
                + " RETURNING id")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void insertAgentConnectionToken(java.sql.Statement s, String organizationId, long connectionId,
            String tokenHash) throws Exception {
        s.execute("INSERT INTO agent_connection_token (organization_id, connection_id, kind, token_hash, expires_at,"
                + " created_at, updated_at, version) VALUES ('" + organizationId + "', " + connectionId
                + ", 'ACCESS', '" + tokenHash + "', now() + interval '1 hour', now(), now(), 0)");
    }

    /**
     * {@code onboarding_path} (V99) is scoped by {@code app.current_subject}, not {@code app.current_org}: a person is
     * onboarded to axonia, not to a tenant. {@code OnboardingPathRepositoryTest} runs on {@code jdbc:tc}, which
     * connects as the table OWNER and bypasses this policy by ownership, a trap that already shipped once. Only this
     * test, as {@code axonia_app}, proves Postgres actually refuses one person's row to another.
     */
    @Test
    @IsolationProbe("onboarding_path")
    void onboardingPathIsSubjectIsolated() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given - a path assigned to person-a, in person-a's own scope
            s.execute("SET app.current_subject = 'rls-onboarding-a'");
            s.execute("INSERT INTO onboarding_path (subject, variant, community_slug, anchor_organization_id,"
                    + " anchor_community_id, created_at, updated_at)"
                    + " VALUES ('rls-onboarding-a','ORGANIZER','platform-engineering','org-1',1, now(), now())");

            // and - the row genuinely exists: person-a's own scope sees it before anybody else's scope is tried
            try (var rs = s.executeQuery("SELECT count(*) FROM onboarding_path WHERE subject = 'rls-onboarding-a'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }

            // when person-b's scope looks for person-a's path
            s.execute("SET app.current_subject = 'rls-onboarding-b'");
            try (var rs = s.executeQuery("SELECT count(*) FROM onboarding_path WHERE subject = 'rls-onboarding-a'")) {
                rs.next();

                // then
                assertThat(rs.getInt(1)).isZero(); // person-b cannot see person-a's path
            }
        }
    }

    /** The other half of the same proof: with no subject at all in scope, the policy fails closed. */
    @Test
    @IsolationProbe("onboarding_path")
    void unsetSubjectSeesNoOnboardingPathRows() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given - a path exists, inserted under its own subject scope
            s.execute("SET app.current_subject = 'rls-onboarding-c'");
            s.execute("INSERT INTO onboarding_path (subject, variant, community_slug, anchor_organization_id,"
                    + " anchor_community_id, created_at, updated_at)"
                    + " VALUES ('rls-onboarding-c','MEMBER','platform-engineering','org-1',1, now(), now())");

            // and - the row genuinely exists: its own scope sees it before the scope is dropped
            try (var rs = s.executeQuery("SELECT count(*) FROM onboarding_path WHERE subject = 'rls-onboarding-c'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }

            // when no subject scope is set at all
            s.execute("RESET app.current_subject");

            // then - fail-closed: nobody sees anything
            try (var rs = s.executeQuery("SELECT count(*) FROM onboarding_path")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    /**
     * {@code onboarding_step} (V99) carries its own {@code onboarding_step_subject_isolation} policy, declared as a
     * separate {@code CREATE POLICY} from the path's. The two tests above only ever touch {@code onboarding_path}, so a
     * typo or a missing {@code WITH CHECK} on the step policy would go undetected by every test in this suite. The
     * parent {@code onboarding_path} row is inserted first because the step's foreign key requires it to exist; the FK
     * check itself runs without regard to the path's own RLS policy, which is exactly why it is not what proves
     * isolation here - the step's own policy is.
     */
    @Test
    @IsolationProbe("onboarding_step")
    void onboardingStepIsSubjectIsolated() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given - a step assigned to person-d, in person-d's own scope
            s.execute("SET app.current_subject = 'rls-onboarding-step-a'");
            s.execute("INSERT INTO onboarding_path (subject, variant, community_slug, anchor_organization_id,"
                    + " anchor_community_id, created_at, updated_at)"
                    + " VALUES ('rls-onboarding-step-a','ORGANIZER','platform-engineering','org-1',1, now(), now())");
            s.execute("INSERT INTO onboarding_step (subject, ordinal, step_key, route)"
                    + " VALUES ('rls-onboarding-step-a', 0, 'LOOK_AND_FEEL',"
                    + " '/communities/platform-engineering/look-and-feel')");

            // and - the row genuinely exists: person-d's own scope sees it before anybody else's scope is tried
            try (var rs = s
                    .executeQuery("SELECT count(*) FROM onboarding_step WHERE subject = 'rls-onboarding-step-a'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }

            // when person-e's scope looks for person-d's step
            s.execute("SET app.current_subject = 'rls-onboarding-step-b'");
            try (var rs = s
                    .executeQuery("SELECT count(*) FROM onboarding_step WHERE subject = 'rls-onboarding-step-a'")) {
                rs.next();

                // then
                assertThat(rs.getInt(1)).isZero(); // person-e cannot see person-d's step
            }
        }
    }

    /**
     * The other half of the same proof, for {@code onboarding_step}: with no subject at all in scope, the policy fails
     * closed.
     */
    @Test
    @IsolationProbe("onboarding_step")
    void unsetSubjectSeesNoOnboardingStepRows() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given - a step exists, inserted under its own subject scope
            s.execute("SET app.current_subject = 'rls-onboarding-step-c'");
            s.execute("INSERT INTO onboarding_path (subject, variant, community_slug, anchor_organization_id,"
                    + " anchor_community_id, created_at, updated_at)"
                    + " VALUES ('rls-onboarding-step-c','MEMBER','platform-engineering','org-1',1, now(), now())");
            s.execute("INSERT INTO onboarding_step (subject, ordinal, step_key, route)"
                    + " VALUES ('rls-onboarding-step-c', 0, 'FIRST_SESSION',"
                    + " '/communities/platform-engineering/schedule')");

            // and - the row genuinely exists: its own scope sees it before the scope is dropped
            try (var rs = s
                    .executeQuery("SELECT count(*) FROM onboarding_step WHERE subject = 'rls-onboarding-step-c'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }

            // when no subject scope is set at all
            s.execute("RESET app.current_subject");

            // then - fail-closed: nobody sees anything
            try (var rs = s.executeQuery("SELECT count(*) FROM onboarding_step")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    @Test
    @IsolationProbe("erasure_receipt")
    void anErasureReceiptIsReadAndWrittenOnlyByItsOwnOrganization() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given
            s.execute("SET app.current_org = 'default'");
            s.execute("INSERT INTO erasure_receipt (organization_id, surrogate_id, erased_at, deleted_row_count,"
                    + " cleared_row_count, anonymised_row_count, media_residue)"
                    + " VALUES ('default','erased-rls-1', now(), 1, 0, 0, 0)");
            s.execute("SET app.current_org = 'acme'");

            // when
            try (var rs = s.executeQuery("SELECT count(*) FROM erasure_receipt WHERE surrogate_id = 'erased-rls-1'")) {
                rs.next();

                // then
                assertThat(rs.getInt(1)).isZero();
            }
            assertThatThrownBy(() -> s.execute("INSERT INTO erasure_receipt (organization_id, surrogate_id, erased_at,"
                    + " deleted_row_count, cleared_row_count, anonymised_row_count, media_residue)"
                    + " VALUES ('default','erased-rls-2', now(), 1, 0, 0, 0)"))
                    .hasMessageContaining("row-level security");
        }
    }
}
