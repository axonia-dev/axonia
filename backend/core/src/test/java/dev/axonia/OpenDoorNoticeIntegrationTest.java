package dev.axonia;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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

import dev.axonia.communication.application.NotificationCentreService;
import dev.axonia.communication.application.SweepUnit;
import dev.axonia.communication.domain.NotificationEntry;
import dev.axonia.expertise.application.ExpertiseDeclarationService;
import dev.axonia.shared.api.tenancy.OrganizationScope;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Declaring an area through the real declare path tells a member who wants to learn it, once, on their panel and never
 * by mail: through the event, the learning list, the outbox and the drain to the panel's read.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class OpenDoorNoticeIntegrationTest {

    private static final String ORG = "open-door-org";
    private static final String SLUG = "open-door-guild";
    private static final String DECLARER = "open-door-declarer";
    private static final String LEARNER = "open-door-learner";

    @Autowired
    private ExpertiseDeclarationService declarations;

    @Autowired
    private NotificationCentreService notifications;

    @Autowired
    private SweepUnit sweep;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedTheGuild() {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES (?, 'Open Door Guild', now(), now(), 0)", ORG);
        var communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, description, status, kind_id, created_at, "
                        + "updated_at, version) VALUES (?, ?, 'Platform Guild', '', 'ACTIVE', (SELECT id FROM "
                        + "community_kind WHERE organization_id = ? AND label = 'Open Door Guild'), now(), now(), 0) "
                        + "RETURNING id",
                Long.class, ORG, SLUG, ORG);
        member(communityId, DECLARER, "Lea Moser");
        member(communityId, LEARNER, "Ben Learner");
        jdbc.update("INSERT INTO learning_intent (organization_id, community_id, user_id, tag, declared_at) "
                + "VALUES (?, ?, ?, 'observability', now())", ORG, communityId, LEARNER);
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void declaringNotifiesALearnerOnce() {

        // when
        signInAs(DECLARER);
        declarations.declare(SLUG, "observability");
        drain();

        // then
        var entries = panelOf(LEARNER);
        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.headline()).isEqualTo("Lea Moser is now open to questions on observability");
            assertThat(entry.line()).isEqualTo("Platform Guild");
            assertThat(entry.actionUrl()).endsWith("/communities/" + SLUG + "/expertise?term=observability&who=open");
        });
        assertThat(mailRows()).isZero();

        // when - the declarer removes the area and declares it again
        signInAs(DECLARER);
        declarations.remove(SLUG, "observability");
        declarations.declare(SLUG, "observability");
        drain();

        // then
        assertThat(panelOf(LEARNER)).hasSize(1);
        assertThat(panelOf(DECLARER)).isEmpty();
    }

    private void member(long communityId, String userId, String displayName) {

        jdbc.update("""
                INSERT INTO identity_user (organization_id, user_id, display_name, email, active, scim_id, arrival,
                                           created_at, updated_at, version)
                VALUES (?, ?, ?, ? || '@example.test', true, 'scim-' || ?, 'SYNCED', now(), now(), 0)
                """, ORG, userId, displayName, userId, userId);
        jdbc.update("INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                + "version) VALUES (?, ?, ?, 'MEMBER', now(), now(), 0)", ORG, communityId, userId);
    }

    private void signInAs(String userId) {

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(userId).claim("org", ORG).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private void drain() {

        OrganizationScope.runAs(ORG, () -> sweep.drain(ORG, Instant.now().plus(Duration.ofMinutes(1))));
    }

    private List<NotificationEntry> panelOf(String userId) {

        signInAs(userId);
        return notifications.page(null, null, 20).entries();
    }

    private int mailRows() {

        return jdbc.queryForObject(
                "SELECT count(*) FROM communication_delivery WHERE organization_id = ? " + "AND channel = 'EMAIL'",
                Integer.class, ORG);
    }
}
