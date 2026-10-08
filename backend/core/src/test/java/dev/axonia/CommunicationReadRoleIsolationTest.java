package dev.axonia;

import java.sql.DriverManager;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * The communication sweep's privilege boundary. axonia_communication_read must see every Organization's id,
 * lifecycle_state and kind, cross-org, with no {@code app.current_org} scope needed - and nothing else at all: no other
 * column of {@code organization}, no other table, no write of any kind. axonia_app stays fail-closed cross-org, exactly
 * as it does for every other privileged role this codebase has added.
 */
class CommunicationReadRoleIsolationTest {

    static DedicatedPostgresContainer pg;

    @BeforeAll
    static void start() {
        pg = new DedicatedPostgresContainer();
        pg.start();
        MigrationChain.SELF_HOSTED.on(pg).migrate();
        seedTwoOrganizations();
    }

    /** Seeded as the table owner (bypasses RLS by ownership) so the rows genuinely exist for both roles to probe. */
    private static void seedTwoOrganizations() {
        try (var c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
                var s = c.createStatement()) {
            s.execute("INSERT INTO organization (id, name, kind, created_at, updated_at)"
                    + " VALUES ('cr-ent','Enterprise One','ENTERPRISE', now(), now()),"
                    + "        ('cr-hub','Hub One','HUB', now(), now())");
        } catch (Exception e) {
            throw new IllegalStateException("could not seed the probe organizations", e);
        }
    }

    @AfterAll
    static void stop() {
        if (pg != null)
            pg.stop();
    }

    private java.sql.Connection asApp() throws Exception {
        return DriverManager.getConnection(pg.getJdbcUrl(), "axonia_app", "axonia_app");
    }

    private java.sql.Connection asCommunicationRead() throws Exception {
        return DriverManager.getConnection(pg.getJdbcUrl(), "axonia_communication_read", "communication_read");
    }

    @Test
    void communicationRead_seesEveryOrganizationIdWithNoScopeSet() throws Exception {
        try (var c = asCommunicationRead(); var s = c.createStatement()) {
            try (var rs = s.executeQuery("SELECT count(*) FROM organization WHERE id IN ('cr-ent','cr-hub')")) {
                rs.next();

                // then - cross-org, no app.current_org needed
                assertThat(rs.getInt(1)).isEqualTo(2);
            }
        }
    }

    @Test
    void communicationRead_canSelectIdLifecycleStateAndKind() throws Exception {
        try (var c = asCommunicationRead(); var s = c.createStatement()) {
            // given - id, lifecycle_state and kind (V45) all succeed
            assertThatCode(() -> {
                try (var rs = s.executeQuery("SELECT id FROM organization WHERE id = 'cr-ent'")) {
                    rs.next();
                }
            }).doesNotThrowAnyException();
            assertThatCode(() -> {
                try (var rs = s.executeQuery("SELECT lifecycle_state FROM organization WHERE id = 'cr-ent'")) {
                    rs.next();
                }
            }).doesNotThrowAnyException();
            assertThatCode(() -> {
                try (var rs = s.executeQuery("SELECT kind FROM organization WHERE id = 'cr-ent'")) {
                    rs.next();
                }
            }).doesNotThrowAnyException();

            // then - every other column of the same table is still denied, proving the grant stays column-scoped
            assertThatThrownBy(() -> s.executeQuery("SELECT name FROM organization WHERE id = 'cr-ent'"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> s.executeQuery("SELECT closed_by FROM organization WHERE id = 'cr-ent'"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> s.executeQuery("SELECT * FROM organization WHERE id = 'cr-ent'"))
                    .hasMessageContaining("permission denied");
        }
    }

    /**
     * The exact query shape {@code OrganizationIds#all} runs in production: {@code id}, filtered by
     * {@code lifecycle_state = 'ACTIVE'}, no scope. Both seeded organizations default to {@code ACTIVE} (V15's column
     * default), so both are expected back.
     */
    @Test
    void communicationRead_canRunTheRealQueryShape() throws Exception {
        try (var c = asCommunicationRead(); var s = c.createStatement()) {
            try (var rs = s.executeQuery(
                    "SELECT id FROM organization WHERE lifecycle_state = 'ACTIVE' AND id IN ('cr-ent','cr-hub')")) {
                var ids = new java.util.ArrayList<String>();
                while (rs.next()) {
                    ids.add(rs.getString(1));
                }
                assertThat(ids).containsExactlyInAnyOrder("cr-ent", "cr-hub");
            }
        }
    }

    /**
     * The exact query shape {@code OrganizationIds#ofKind} runs in production (a sweep that meters organizations by
     * kind): {@code id}, filtered by {@code lifecycle_state = 'ACTIVE'} and {@code kind}, no scope. Regression test for
     * the missing {@code kind} column grant V45 fixed: before that migration this statement failed with "permission
     * denied for table organization", which is exactly the failure that aborted that sweep's tick in production before
     * anything downstream ever ran. Only {@code cr-ent} is ENTERPRISE, so only it comes back.
     */
    @Test
    void communicationRead_canRunTheOfKindQueryShape() throws Exception {
        try (var c = asCommunicationRead(); var s = c.createStatement()) {
            try (var rs = s.executeQuery("SELECT id FROM organization WHERE lifecycle_state = 'ACTIVE' AND "
                    + "kind = 'ENTERPRISE' AND id IN ('cr-ent','cr-hub')")) {
                var ids = new java.util.ArrayList<String>();
                while (rs.next()) {
                    ids.add(rs.getString(1));
                }
                assertThat(ids).containsExactly("cr-ent");
            }
        }
    }

    @Test
    void communicationRead_cannotWriteToOrganization() throws Exception {
        try (var c = asCommunicationRead(); var s = c.createStatement()) {
            assertThatThrownBy(() -> s.execute("INSERT INTO organization (id, name, kind, created_at, updated_at)"
                    + " VALUES ('sneaky','X','HUB', now(), now())")).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> s.execute("UPDATE organization SET name='hacked' WHERE id='cr-ent'"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> s.execute("DELETE FROM organization WHERE id='cr-ent'"))
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void communicationRead_cannotReadAnyOtherTable() throws Exception {
        try (var c = asCommunicationRead(); var s = c.createStatement()) {
            // then - containment: the role holds no grant at all outside organization.id
            assertThatThrownBy(() -> s.executeQuery("SELECT count(*) FROM identity_user"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> s.executeQuery("SELECT count(*) FROM communication_delivery"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> s.executeQuery("SELECT count(*) FROM communication_preference"))
                    .hasMessageContaining("permission denied");
        }
    }

    /**
     * Checks {@code cr-ent} (ENTERPRISE) only, not {@code cr-hub}: the {@code organization_public_hub_card_directory}
     * policy - unrelated to this migration - makes every public hub visible to the app role by design (the public hub
     * directory), so a hub's visibility here says nothing about what this test is guarding.
     */
    @Test
    void appRole_staysFailClosedCrossOrgAfterV30() throws Exception {
        try (var c = asApp(); var s = c.createStatement()) {
            // given - no scope at all
            s.execute("RESET app.current_org");
            try (var rs = s.executeQuery("SELECT count(*) FROM organization WHERE id = 'cr-ent'")) {
                rs.next();

                // then - the new communication_read policy did not loosen axonia_app by one row
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }
}
