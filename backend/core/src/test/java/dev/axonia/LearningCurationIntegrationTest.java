package dev.axonia;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import dev.axonia.expertise.application.ExpertiseCurationService;
import dev.axonia.expertise.domain.ExpertiseArea;
import dev.axonia.shared.api.tenancy.OrganizationScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * An area a member wants to learn is stored under the spelling they typed, so a curator's rename and delete reach it
 * through the real curation service, a name only an intent carries is still one the curator can see and act on, and a
 * member who leaves through the real leave path, their own or an erasure's, takes their intents there with them.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class LearningCurationIntegrationTest {

    private static final String ORG = "default";
    private static final Instant EARLIER = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant LATER = Instant.parse("2026-09-10T09:00:00Z");

    /** A slug and members of their own per test: the fixtures commit, and are only truncated once the class is done. */
    private static final AtomicInteger RUNS = new AtomicInteger();

    @Autowired
    private ExpertiseCurationService curation;

    @Autowired
    private MembershipService memberships;

    @Autowired
    private JdbcTemplate jdbc;

    private String slug;
    private long communityId;
    private String alice;
    private String bob;

    @BeforeEach
    void seedACommunityWhoseMembersWantToLearn() {

        var run = RUNS.incrementAndGet();
        slug = "learning-curation-" + run;
        var curator = "learning-curator-" + run;
        alice = "learning-alice-" + run;
        bob = "learning-bob-" + run;
        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES (?, 'Learning Guild', now(), now(), 0) ON CONFLICT DO NOTHING", ORG);
        communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, description, status, kind_id, created_at, "
                        + "updated_at, version) VALUES (?, ?, ?, '', 'ACTIVE', (SELECT id FROM community_kind "
                        + "WHERE organization_id = ? AND label = 'Learning Guild'), now(), now(), 0) RETURNING id",
                Long.class, ORG, slug, slug, ORG);
        join(curator, "ORGANIZER");
        join(alice, "MEMBER");
        join(bob, "MEMBER");
        want(alice, "k8s", EARLIER);
        want(alice, "kubernetes", LATER);
        want(bob, "helm", EARLIER);
        actAs(curator);
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void renameFoldsAnIntentKeepingTheEarlierDate() {

        // when
        curation.rename(slug, "k8s", "kubernetes");

        // then
        assertThat(intentsOf(alice)).extracting("tag", "declared_at")
                .containsExactly(tuple("kubernetes", Timestamp.from(EARLIER)));
        assertThat(intentsOf(bob)).extracting("tag").containsExactly("helm");
    }

    @Test
    void deleteNamesAndRemovesIntents() {

        // when
        var usage = curation.usage(slug, "helm");

        // then
        assertThat(usage.learningIntents()).isEqualTo(1);

        // when
        curation.delete(slug, "helm");

        // then
        assertThat(intentsOf(bob)).isEmpty();
        assertThat(intentsOf(alice)).hasSize(2);
    }

    @Test
    void anIntentOnlyTagIsInTheCurationVocabulary() {

        // when
        var actual = curation.vocabulary(slug);

        // then
        assertThat(actual).extracting(ExpertiseArea::canonicalTag).contains("helm", "k8s", "kubernetes");
    }

    @Test
    void leavingDeletesTheMembersIntents() {

        // given
        actAs(bob);

        // when
        memberships.leave(slug);

        // then
        assertThat(intentsOf(bob)).isEmpty();
        assertThat(intentsOf(alice)).extracting("tag").containsExactlyInAnyOrder("k8s", "kubernetes");
    }

    /**
     * Erasure leaves every community through {@code leaveForErasure} under {@link OrganizationScope}, with no member
     * token at all, so the listener can only find the organization on the event it is handed.
     */
    @Test
    void anErasuresLeaveWithNoMemberTokenDeletesTheMembersIntents() {

        // given
        SecurityContextHolder.clearContext();

        // when
        OrganizationScope.runAs(ORG, () -> memberships.leaveForErasure(ORG, communityId, bob));

        // then
        assertThat(intentsOf(bob)).isEmpty();
        assertThat(intentsOf(alice)).extracting("tag").containsExactlyInAnyOrder("k8s", "kubernetes");
    }

    private void join(String userId, String role) {

        jdbc.update("INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                + "version) VALUES (?, ?, ?, ?, now(), now(), 0)", ORG, communityId, userId, role);
    }

    private void want(String userId, String tag, Instant declaredAt) {

        jdbc.update("INSERT INTO learning_intent (organization_id, community_id, user_id, tag, declared_at) "
                + "VALUES (?, ?, ?, ?, ?)", ORG, communityId, userId, tag, Timestamp.from(declaredAt));
    }

    private List<Map<String, Object>> intentsOf(String userId) {

        return jdbc.queryForList("SELECT tag, declared_at FROM learning_intent WHERE organization_id = ? "
                + "AND community_id = ? AND user_id = ? ORDER BY tag", ORG, communityId, userId);
    }

    private static void actAs(String userId) {

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(userId).claim("org", ORG).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
