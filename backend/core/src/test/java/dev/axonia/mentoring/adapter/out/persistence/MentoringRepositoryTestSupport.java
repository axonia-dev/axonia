package dev.axonia.mentoring.adapter.out.persistence;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.mentoring.domain.Pairing;

/** Seeds the community, directory rows and memberships the mentoring repositories read; the baseline seeds nothing. */
abstract class MentoringRepositoryTestSupport extends CoreJdbcTestSupport {

    static final String ORG = "default";

    @Autowired
    protected NamedParameterJdbcTemplate jdbc;

    protected long seedCommunity(String slug) {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES (:org, 'Guild', now(), now(), 0) ON CONFLICT DO NOTHING", Map.of("org", ORG));
        var kindId = jdbc.queryForObject(
                "SELECT id FROM community_kind WHERE organization_id = :org AND label = 'Guild'", Map.of("org", ORG),
                Long.class);
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, "
                        + "updated_at, version) VALUES (:org, :slug, :slug, :kindId, '', 'ACTIVE', now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", ORG, "slug", slug, "kindId", kindId), Long.class);
    }

    protected void seedMember(long communityId, String userId, boolean active) {

        seedUser(userId, active);
        jdbc.update(
                "INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                        + "version) VALUES (:org, :community, :userId, 'MEMBER', now(), now(), 0)",
                Map.of("org", ORG, "community", communityId, "userId", userId));
    }

    protected void seedUser(String userId, boolean active) {

        jdbc.update("""
                INSERT INTO identity_user (organization_id, user_id, display_name, email, active, scim_id, arrival,
                                           created_at, updated_at, version)
                VALUES (:org, :userId, :userId, :userId || '@example.test', :active, 'scim-' || :userId, 'SYNCED',
                        '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', 0)
                """, new MapSqlParameterSource("org", ORG).addValue("userId", userId).addValue("active", active));
    }

    protected static Pairing.PairingBuilder pairing(long communityId, String mentor, String mentee) {

        return Pairing.builder().communityId(communityId).mentorUserId(mentor).menteeUserId(mentee)
                .startedAt(java.time.Instant.parse("2026-10-01T09:00:00Z"));
    }
}
