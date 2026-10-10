package dev.axonia;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import dev.axonia.community.application.MembershipService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A member who leaves a community through the real leave path is no longer open to questions there, and still is in
 * every community they stayed in.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class DeclarationDepartureIntegrationTest {

    private static final String ORG = "default";
    private static final String MEMBER = "departing-declarer";

    /** A slug of its own per community: the fixtures commit, and are only truncated once the class is done. */
    private static final AtomicInteger COMMUNITIES = new AtomicInteger();

    @Autowired
    private MembershipService memberships;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void authenticate() {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES (?, 'Departure Guild', now(), now(), 0) ON CONFLICT DO NOTHING", ORG);
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(MEMBER).claim("org", ORG).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void leavingACommunityDeletesTheDeclarationsThereAndKeepsTheOthers() {

        // given
        var left = "departure-left-" + COMMUNITIES.incrementAndGet();
        var stayed = "departure-stayed-" + COMMUNITIES.incrementAndGet();
        var leftId = communityWithMember(left);
        var stayedId = communityWithMember(stayed);
        declare(leftId, "kafka");
        declare(leftId, "terraform");
        declare(stayedId, "kafka");

        // when
        memberships.leave(left);

        // then
        assertThat(declarationCount(leftId)).isZero();
        assertThat(declarationCount(stayedId)).isEqualTo(1);
    }

    private long communityWithMember(String slug) {

        var communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, description, status, kind_id, created_at, "
                        + "updated_at, version) VALUES (?, ?, ?, '', 'ACTIVE', (SELECT id FROM community_kind "
                        + "WHERE organization_id = ? AND label = 'Departure Guild'), now(), now(), 0) RETURNING id",
                Long.class, ORG, slug, slug, ORG);
        jdbc.update("INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                + "version) VALUES (?, ?, ?, 'MEMBER', now(), now(), 0)", ORG, communityId, MEMBER);
        return communityId;
    }

    private void declare(long communityId, String tag) {

        jdbc.update("INSERT INTO expertise_declaration (organization_id, community_id, user_id, tag, declared_at) "
                + "VALUES (?, ?, ?, ?, now())", ORG, communityId, MEMBER, tag);
    }

    private int declarationCount(long communityId) {

        return jdbc.queryForObject("SELECT count(*) FROM expertise_declaration WHERE organization_id = ? "
                + "AND community_id = ? AND user_id = ?", Integer.class, ORG, communityId, MEMBER);
    }
}
