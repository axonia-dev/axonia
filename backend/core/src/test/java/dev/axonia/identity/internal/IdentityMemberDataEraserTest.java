package dev.axonia.identity.internal;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.FootprintTestSupport;
import dev.axonia.MemberErasure;
import dev.axonia.media.MediaStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

class IdentityMemberDataEraserTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";
    private static final String TARGET_SCIM_ID = "target-scim-id";
    private static final String NEIGHBOUR_SCIM_ID = "neighbour-scim-id";
    private static final String TARGET_AVATAR_KEY = "avatars/default/target.jpg";
    private static final String NEIGHBOUR_AVATAR_KEY = "avatars/default/neighbour.jpg";
    private static final String TARGET_OVERRIDE_KEY = "avatars/default/target-own.png";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private IdentityMemberDataEraser eraser;

    @Autowired
    private MediaStorage mediaStorage;

    @BeforeEach
    void resetSharedMock() {

        // FootprintTestSupport shares one context (and one MediaStorage mock) across all footprint/eraser tests.
        reset(mediaStorage);
    }

    @AfterEach
    void clearStubsOffTheSharedMock() {

        // A doThrow(...) stub left on this singleton would leak into whichever test runs next in either suite order.
        reset(mediaStorage);
    }

    @Test
    void deletesEveryIdentityRowForTheTargetInLoadBearingOrderAndSparesTheNeighbour() {

        var groupId = seedDirectoryGroup();
        seedIdentityUser(TARGET, TARGET_SCIM_ID, TARGET_AVATAR_KEY);
        seedIdentityUser(NEIGHBOUR, NEIGHBOUR_SCIM_ID, NEIGHBOUR_AVATAR_KEY);
        seedPersonalAccessToken(TARGET);
        seedPersonalAccessToken(NEIGHBOUR);
        seedDirectoryGroupMember(groupId, TARGET_SCIM_ID);
        seedDirectoryGroupMember(groupId, NEIGHBOUR_SCIM_ID);

        var counts = eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET)
                .surrogateId("erased:test-surrogate").erasedAt(Instant.parse("2026-07-27T10:00:00Z")).build());

        assertThat(counts.deleted()).isEqualTo(3);
        assertThat(counts.mediaResidue()).isZero();
        assertThat(countIdentityUser(TARGET)).isZero();
        assertThat(countIdentityUser(NEIGHBOUR)).isEqualTo(1);
        assertThat(countPersonalAccessToken(TARGET)).isZero();
        assertThat(countPersonalAccessToken(NEIGHBOUR)).isEqualTo(1);
        assertThat(countDirectoryGroupMember(TARGET_SCIM_ID)).isZero();
        assertThat(countDirectoryGroupMember(NEIGHBOUR_SCIM_ID)).isEqualTo(1);
        verify(mediaStorage).delete(TARGET_AVATAR_KEY);
        verify(mediaStorage, never()).delete(NEIGHBOUR_AVATAR_KEY);
    }

    @Test
    void erasesAMemberWithNoDirectoryGroupMembershipWithoutFailing() {

        // a member who was never synced through a directory group has nothing in directory_group_member to touch
        jdbc.update(
                "INSERT INTO identity_user (organization_id, user_id, display_name, scim_id, arrival, created_at, "
                        + "updated_at, version) VALUES (:org, :user, :user, :scimId, 'SYNCED', now(), now(), 0)",
                Map.of("org", ORG, "user", TARGET, "scimId", "scim-" + TARGET));

        var counts = eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET)
                .surrogateId("erased:test-surrogate").erasedAt(Instant.parse("2026-07-27T10:00:00Z")).build());

        assertThat(counts.deleted()).isEqualTo(1);
        assertThat(counts.mediaResidue()).isZero();
        assertThat(countIdentityUser(TARGET)).isZero();
    }

    @Test
    void toleratesAStorageFailureAndCountsTheResidueInsteadOfFailingTheErasure() {

        seedIdentityUser(TARGET, TARGET_SCIM_ID, TARGET_AVATAR_KEY);
        doThrow(new IllegalStateException("object store down")).when(mediaStorage).delete(TARGET_AVATAR_KEY);

        var counts = eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET)
                .surrogateId("erased:test-surrogate").erasedAt(Instant.parse("2026-07-27T10:00:00Z")).build());

        assertThat(counts.mediaResidue()).isEqualTo(1);
        assertThat(countIdentityUser(TARGET)).isZero();
    }

    @Test
    void erasureDeletesBothTheMirroredBlobAndTheOverride() {

        seedIdentityUserWithOverride(TARGET, TARGET_SCIM_ID, TARGET_AVATAR_KEY, TARGET_OVERRIDE_KEY);

        var counts = eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET)
                .surrogateId("erased:test-surrogate").erasedAt(Instant.parse("2026-07-27T10:00:00Z")).build());

        assertThat(counts.mediaResidue()).isZero();
        verify(mediaStorage).delete(TARGET_AVATAR_KEY);
        verify(mediaStorage).delete(TARGET_OVERRIDE_KEY);
    }

    @Test
    void aFailedMirroredAvatarDeleteStillAttemptsTheOverrideAndOnlyItsOwnFailureLandsAsResidue() {

        seedIdentityUserWithOverride(TARGET, TARGET_SCIM_ID, TARGET_AVATAR_KEY, TARGET_OVERRIDE_KEY);
        doThrow(new IllegalStateException("object store down")).when(mediaStorage).delete(TARGET_AVATAR_KEY);

        var counts = eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET)
                .surrogateId("erased:test-surrogate").erasedAt(Instant.parse("2026-07-27T10:00:00Z")).build());

        verify(mediaStorage).delete(TARGET_AVATAR_KEY);
        verify(mediaStorage).delete(TARGET_OVERRIDE_KEY);
        assertThat(counts.mediaResidue()).isEqualTo(1);
    }

    @Test
    void erasesTheTargetsAgentConnectionsAndTheirTokensAndSparesTheNeighbour() {

        var targetConnection = seedAgentConnection(TARGET);
        var neighbourConnection = seedAgentConnection(NEIGHBOUR);
        seedAgentConnectionToken(targetConnection, "target-token-hash");
        seedAgentConnectionToken(neighbourConnection, "neighbour-token-hash");

        eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId("erased:test-surrogate")
                .erasedAt(Instant.parse("2026-09-24T10:00:00Z")).build());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_connection WHERE user_id = :user",
                Map.of("user", TARGET), Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_connection_token WHERE token_hash = :hash",
                Map.of("hash", "target-token-hash"), Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_connection WHERE user_id = :user",
                Map.of("user", NEIGHBOUR), Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_connection_token WHERE token_hash = :hash",
                Map.of("hash", "neighbour-token-hash"), Long.class)).isEqualTo(1);
    }

    private Long seedAgentConnection(String userId) {

        return jdbc.queryForObject("INSERT INTO agent_connection (organization_id, user_id, client_id, client_name, "
                + "client_host, scopes, resource, redirect_uri, code_challenge, activated_at, ends_at, created_at, "
                + "updated_at, version) VALUES (:org, :user, 'https://claude.ai/cc', 'Claude Code', 'claude.ai', "
                + "'read', 'https://axonia.test/mcp', 'http://localhost/callback', 'challenge', now(), "
                + "now() + interval '1 day', now(), now(), 0) RETURNING id", Map.of("org", ORG, "user", userId),
                Long.class);
    }

    private void seedAgentConnectionToken(Long connectionId, String hash) {

        jdbc.update(
                "INSERT INTO agent_connection_token (organization_id, connection_id, kind, token_hash, "
                        + "expires_at, created_at, updated_at, version) VALUES (:org, :connection, 'ACCESS', :hash, "
                        + "now() + interval '1 hour', now(), now(), 0)",
                Map.of("org", ORG, "connection", connectionId, "hash", hash));
    }

    private Long seedDirectoryGroup() {

        return jdbc.queryForObject(
                "INSERT INTO directory_group (organization_id, scim_id, external_id, display_name, "
                        + "reconciliation_key, created_at, updated_at, version) VALUES (:org, "
                        + "'eraser-test-group-scim-id', 'eraser-test-group-external-id', 'Eraser Test Group', "
                        + "'eraser-test-reconciliation-key', now(), now(), 0) RETURNING id",
                Map.of("org", ORG), Long.class);
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

    private void seedPersonalAccessToken(String userId) {

        jdbc.update("INSERT INTO personal_access_token (organization_id, user_id, token_hash, name, scopes, "
                + "created_at, updated_at, version) VALUES (:org, :user, :hash, 'Eraser Test PAT', 'read', now(), "
                + "now(), 0)", Map.of("org", ORG, "user", userId, "hash", "eraser-test-pat-hash-" + userId));
    }

    private void seedDirectoryGroupMember(Long groupId, String userScimId) {

        jdbc.update(
                "INSERT INTO directory_group_member (organization_id, group_id, user_scim_id, created_at, "
                        + "updated_at, version) VALUES (:org, :groupId, :scimId, now(), now(), 0)",
                Map.of("org", ORG, "groupId", groupId, "scimId", userScimId));
    }

    private long countIdentityUser(String userId) {

        return jdbc.queryForObject(
                "SELECT count(*) FROM identity_user WHERE organization_id = :org AND user_id = :user",
                Map.of("org", ORG, "user", userId), Long.class);
    }

    private long countPersonalAccessToken(String userId) {

        return jdbc.queryForObject(
                "SELECT count(*) FROM personal_access_token WHERE organization_id = :org AND user_id = :user",
                Map.of("org", ORG, "user", userId), Long.class);
    }

    private long countDirectoryGroupMember(String userScimId) {

        return jdbc.queryForObject(
                "SELECT count(*) FROM directory_group_member WHERE organization_id = :org AND user_scim_id = :scimId",
                Map.of("org", ORG, "scimId", userScimId), Long.class);
    }
}
