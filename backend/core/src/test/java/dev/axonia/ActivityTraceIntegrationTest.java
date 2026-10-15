package dev.axonia;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * The activity trace through the real request stack and the three real adapters: readable by any member of the
 * community's organization, whatever the health policy says, and resolved exactly like the community read.
 */
@CoreSpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class ActivityTraceIntegrationTest {

    private static final String ORG = "default";
    private static final String OTHER_ORG = "pulse-other-org";
    private static final String MEMBER = "pulse-member";
    private static final String OUTSIDER = "pulse-outsider";
    private static final AtomicInteger COMMUNITIES = new AtomicInteger();

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedOrganizations() {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES (?, 'Community of Practice', now(), now(), 0) ON CONFLICT DO NOTHING", ORG);
        jdbc.update("INSERT INTO artifact_type (organization_id, label, created_at, updated_at, version) "
                + "VALUES (?, 'Recording', now(), now(), 0) ON CONFLICT DO NOTHING", ORG);
        for (var org : new String[]{ORG, OTHER_ORG}) {
            jdbc.update("INSERT INTO organization (id, name, kind, created_at, updated_at) "
                    + "VALUES (?, ?, 'ENTERPRISE', now(), now()) ON CONFLICT DO NOTHING", org, org);
        }
        person(ORG, MEMBER);
        person(OTHER_ORG, OUTSIDER);
    }

    @Test
    void aMemberWithoutTheHealthPolicySeesTheTraceWhileHealthStaysClosed() {

        var slug = communityWithActivity("ACTIVE");

        var trace = mvc.get().uri("/axonia/communities/{slug}/activity-trace", slug).with(signedInAs(MEMBER, ORG));
        var health = mvc.get().uri("/axonia/communities/{slug}/health", slug).with(signedInAs(MEMBER, ORG));

        assertThat(trace).hasStatusOk().bodyJson().extractingPath("$.days").isEqualTo(90);
        assertThat(trace).bodyJson().extractingPath("$.sessionsHeld.length()").isEqualTo(90);
        assertThat(trace).bodyJson().extractingPath("$.libraryAdditions.length()").isEqualTo(90);
        assertThat(trace).bodyJson().extractingPath("$.joins.length()").isEqualTo(90);
        assertThat(health).hasStatus(403);
    }

    @Test
    void theCountsLandOnTheirDays() {

        var slug = communityWithActivity("ACTIVE");

        var trace = mvc.get().uri("/axonia/communities/{slug}/activity-trace", slug).with(signedInAs(MEMBER, ORG));

        assertThat(trace).bodyJson().extractingPath("$.sessionsHeld[89]").isEqualTo(1);
        assertThat(trace).bodyJson().extractingPath("$.libraryAdditions[87]").isEqualTo(1);
        assertThat(trace).bodyJson().extractingPath("$.joins[79]").isEqualTo(1);
    }

    @Test
    void anArchivedCommunityStillAnswersItsTrace() {

        var slug = communityWithActivity("ARCHIVED");

        var trace = mvc.get().uri("/axonia/communities/{slug}/activity-trace", slug).with(signedInAs(MEMBER, ORG));

        assertThat(trace).hasStatusOk().bodyJson().extractingPath("$.sessionsHeld[89]").isEqualTo(1);
    }

    @Test
    void anotherOrganizationsSlugAnswersLikeTheCommunityRead() {

        var slug = communityWithActivity("ACTIVE");
        var communityRead = status("/axonia/communities/" + slug, OUTSIDER, OTHER_ORG);

        var actual = status("/axonia/communities/" + slug + "/activity-trace", OUTSIDER, OTHER_ORG);

        assertThat(communityRead).isEqualTo(404);
        assertThat(actual).isEqualTo(communityRead);
    }

    @Test
    void anUnknownSlugAnswersLikeTheCommunityRead() {

        var communityRead = status("/axonia/communities/no-such-pulse", MEMBER, ORG);

        var actual = status("/axonia/communities/no-such-pulse/activity-trace", MEMBER, ORG);

        assertThat(communityRead).isEqualTo(404);
        assertThat(actual).isEqualTo(communityRead);
    }

    private int status(String uri, String userId, String org) {

        return mvc.get().uri(uri).with(signedInAs(userId, org)).exchange().getResponse().getStatus();
    }

    private static RequestPostProcessor signedInAs(String userId, String org) {

        return jwt().jwt(token -> token.subject(userId).claim("org", org));
    }

    private String communityWithActivity(String status) {

        var now = Instant.now();
        var slug = "pulse-trace-" + COMMUNITIES.incrementAndGet();
        var communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, description, status, kind_id, created_at, "
                        + "updated_at, version) VALUES (?, ?, ?, '', ?, "
                        + "(SELECT min(id) FROM community_kind WHERE organization_id = ?), now(), now(), 0) "
                        + "RETURNING id",
                Long.class, ORG, slug, slug, status, ORG);
        jdbc.update(
                "INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                        + "version) VALUES (?, ?, ?, 'MEMBER', ?, now(), 0)",
                ORG, communityId, MEMBER, Timestamp.from(now.minus(Duration.ofDays(10)).minus(Duration.ofHours(12))));
        jdbc.update(
                "INSERT INTO community_session (organization_id, community_id, status, scheduled_at, "
                        + "duration_minutes, notes, recording_url, has_time, created_at, updated_at, version) "
                        + "VALUES (?, ?, 'SCHEDULED', ?, 60, '', '', true, now(), now(), 0)",
                ORG, communityId, Timestamp.from(now.minus(Duration.ofHours(1))));
        jdbc.update(
                "INSERT INTO artifact (organization_id, community_id, type_id, title, description, url, "
                        + "contributor_user_id, status, search_document, created_at, updated_at, version) "
                        + "VALUES (?, ?, (SELECT min(id) FROM artifact_type WHERE organization_id = ?), 'T', 'd', "
                        + "'https://example.test', ?, 'ACTIVE', 'doc', ?, now(), 0)",
                ORG, communityId, ORG, MEMBER, Timestamp.from(now.minus(Duration.ofHours(49))));
        return slug;
    }

    private void person(String org, String userId) {

        jdbc.update("INSERT INTO identity_user (organization_id, user_id, display_name, email, active, scim_id, "
                + "arrival, created_at, updated_at, version) VALUES (?, ?, ?, null, true, ?, 'SYNCED', now(), now(), 0) "
                + "ON CONFLICT DO NOTHING", org, userId, userId, "scim-" + userId);
    }
}
