package dev.axonia.identity.internal;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.PurgerTestSupport;
import dev.axonia.media.MediaStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

class IdentityDataPurgerTest extends PurgerTestSupport {

    private static final String ORG = "default";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private IdentityDataPurger purger;

    @Autowired
    private MediaStorage mediaStorage;

    @BeforeEach
    void resetSharedMock() {

        // PurgerTestSupport shares one context (and one MediaStorage mock) across all purger tests.
        reset(mediaStorage);
    }

    @Test
    void purgeEmptiesEveryIdentityTableForTheOrganization() {

        // given - one row per identity table for org "default", including a group member cascading from its group
        seedIdentityTables();

        // when
        purger.purge(ORG);

        // then
        assertThat(count("identity_user")).isZero();
        assertThat(count("directory_group")).isZero();
        assertThat(count("directory_group_member")).isZero();
        assertThat(count("scim_token")).isZero();
        assertThat(count("personal_access_token")).isZero();
        assertThat(count("agent_connection")).isZero();
        assertThat(count("agent_connection_token")).isZero();
    }

    @Test
    void purgeDeletesSyncedAvatarBlobs() {

        // given - one synced avatar in the doomed org, one in a surviving org
        jdbc.update(
                "INSERT INTO identity_user (organization_id, user_id, display_name, avatar_media_key, scim_id, "
                        + "arrival, created_at, updated_at, version) VALUES (:org, 'avatar-user', 'Avatar User', "
                        + "'avatars/default/doomed.jpg', 'scim-avatar-user', 'SYNCED', now(), now(), 0)",
                Map.of("org", ORG));
        jdbc.update("INSERT INTO identity_user (organization_id, user_id, display_name, avatar_media_key, scim_id, "
                + "arrival, created_at, updated_at, version) VALUES ('acme', 'other-user', 'Other User', "
                + "'avatars/acme/survives.jpg', 'scim-other-user', 'SYNCED', now(), now(), 0)", Map.of());

        // when
        purger.purge(ORG);

        // then
        verify(mediaStorage).delete("avatars/default/doomed.jpg");
        verify(mediaStorage, never()).delete("avatars/acme/survives.jpg");
    }

    @Test
    void purgeDeletesOverrideAvatarBlobsToo() {

        // given - one member's own overridden avatar in the doomed org, one in a surviving org
        jdbc.update(
                "INSERT INTO identity_user (organization_id, user_id, display_name, avatar_override_key, scim_id, "
                        + "arrival, created_at, updated_at, version) VALUES (:org, 'override-user', 'Override User', "
                        + "'avatars/default/own.png', 'scim-override-user', 'SYNCED', now(), now(), 0)",
                Map.of("org", ORG));
        jdbc.update("INSERT INTO identity_user (organization_id, user_id, display_name, avatar_override_key, "
                + "scim_id, arrival, created_at, updated_at, version) VALUES ('acme', 'other-override-user', "
                + "'Other Override User', 'avatars/acme/own.png', 'scim-other-override-user', 'SYNCED', now(), "
                + "now(), 0)", Map.of());

        // when
        purger.purge(ORG);

        // then
        verify(mediaStorage).delete("avatars/default/own.png");
        verify(mediaStorage, never()).delete("avatars/acme/own.png");
    }

    @Test
    void purgeToleratesAStorageFailureAndStillDeletesTheRows() {

        // given
        jdbc.update(
                "INSERT INTO identity_user (organization_id, user_id, display_name, avatar_media_key, scim_id, "
                        + "arrival, created_at, updated_at, version) VALUES (:org, 'avatar-user', 'Avatar User', "
                        + "'avatars/default/doomed.jpg', 'scim-avatar-user', 'SYNCED', now(), now(), 0)",
                Map.of("org", ORG));
        doThrow(new IllegalStateException("object store down")).when(mediaStorage).delete("avatars/default/doomed.jpg");

        // when
        purger.purge(ORG);

        // then - orphaned blob accepted, missing rows not
        assertThat(count("identity_user")).isZero();
    }

    /**
     * Seeds one row in each identity table for org "default"; the group member rides the seeded directory group.
     */
    private void seedIdentityTables() {

        jdbc.update(
                "INSERT INTO identity_user (organization_id, user_id, display_name, email, scim_id, arrival, "
                        + "created_at, updated_at, version) VALUES (:org, 'purger-test-user', 'Purger Test User', "
                        + "'purger-test@example.com', 'scim-purger-test-user', 'SYNCED', now(), now(), 0)",
                Map.of("org", ORG));

        var groupId = jdbc.queryForObject(
                "INSERT INTO directory_group (organization_id, scim_id, external_id, "
                        + "display_name, reconciliation_key, created_at, updated_at, version) VALUES (:org, "
                        + "'purger-test-scim-id', 'purger-test-external-id', 'Purger Test Group', "
                        + "'purger-test-reconciliation-key', now(), now(), 0) RETURNING id",
                Map.of("org", ORG), Long.class);

        jdbc.update(
                "INSERT INTO directory_group_member (organization_id, group_id, user_scim_id, created_at, "
                        + "updated_at, version) VALUES (:org, :groupId, 'purger-test-member-scim-id', now(), now(), 0)",
                Map.of("org", ORG, "groupId", groupId));

        jdbc.update(
                "INSERT INTO scim_token (organization_id, token_hash, label, created_at, updated_at, "
                        + "version) VALUES (:org, 'purger-test-token-hash', 'Purger Test Token', now(), now(), 0)",
                Map.of("org", ORG));

        jdbc.update("INSERT INTO personal_access_token (organization_id, user_id, token_hash, name, scopes, "
                + "created_at, updated_at, version) VALUES (:org, 'purger-test-user', 'purger-test-pat-hash', "
                + "'Purger Test PAT', 'read', now(), now(), 0)", Map.of("org", ORG));

        var connectionId = jdbc.queryForObject(
                "INSERT INTO agent_connection (organization_id, user_id, client_id, client_name, client_host, "
                        + "scopes, resource, redirect_uri, code_challenge, activated_at, ends_at, created_at, "
                        + "updated_at, version) VALUES (:org, 'purger-test-user', 'https://claude.ai/cc', "
                        + "'Claude Code', 'claude.ai', 'read', 'https://axonia.test/mcp', "
                        + "'http://localhost/callback', 'purger-test-challenge', now(), now() + interval '1 day', "
                        + "now(), now(), 0) RETURNING id",
                Map.of("org", ORG), Long.class);
        jdbc.update(
                "INSERT INTO agent_connection_token (organization_id, connection_id, kind, token_hash, "
                        + "expires_at, created_at, updated_at, version) VALUES (:org, :connection, 'ACCESS', "
                        + "'purger-test-connection-token-hash', now() + interval '1 hour', now(), now(), 0)",
                Map.of("org", ORG, "connection", connectionId));
    }

    private long count(String table) {

        return switch (table) {
            case "identity_user" -> jdbc.queryForObject(
                    "SELECT count(*) FROM identity_user WHERE organization_id = :org", Map.of("org", ORG), Long.class);
            case "directory_group" ->
                jdbc.queryForObject("SELECT count(*) FROM directory_group WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            case "directory_group_member" ->
                jdbc.queryForObject("SELECT count(*) FROM directory_group_member WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            case "scim_token" -> jdbc.queryForObject("SELECT count(*) FROM scim_token WHERE organization_id = :org",
                    Map.of("org", ORG), Long.class);
            case "personal_access_token" ->
                jdbc.queryForObject("SELECT count(*) FROM personal_access_token WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            case "agent_connection" ->
                jdbc.queryForObject("SELECT count(*) FROM agent_connection WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            case "agent_connection_token" ->
                jdbc.queryForObject("SELECT count(*) FROM agent_connection_token WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            default -> throw new IllegalArgumentException("Unexpected org-scoped table: " + table);
        };
    }
}
