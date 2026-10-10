package dev.axonia;

import java.sql.Timestamp;
import java.time.Instant;
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

import dev.axonia.expertise.application.ExpertiseService;
import dev.axonia.expertise.domain.SourceType;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A signal outlives the content it credits - the artifact is withdrawn while the projection's delete is still
 * unprocessed, a purge takes the topic, a restore puts back signals whose sources are gone. The evidence view promises
 * to tolerate that: "a source that no longer resolves is simply left out of the result".
 *
 * <p>
 * It did not. Resolving a title went through {@code ArtifactService.get} / {@code TopicService.get}, both
 * {@code @Transactional} and both throwing {@link NotFoundException} on a missing row. A method that joins the caller's
 * transaction and throws marks that shared transaction rollback-only <em>before</em> the exception leaves the proxy, so
 * catching it upstream changed nothing: the read finished, committed, and the commit threw
 * {@code UnexpectedRollbackException}. Every expertise search over a tag with one dead source answered 500.
 *
 * <p>
 * Slots never had the bug - their titles come from a batched reader that returns a map and omits what is missing - so
 * each case below names its source type: the SLOT case is the control that was always green.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class OrphanedEvidenceIntegrationTest {

    private static final String ORG = "default";
    private static final String TAG = "kubernetes";

    /** A community and a member of its own per test: the fixtures commit, and are truncated only per class. */
    private static final AtomicInteger FIXTURES = new AtomicInteger();

    /** No content table reaches this far, so every source id below is guaranteed to resolve to nothing. */
    private static final long GONE = 999_000L;

    @Autowired
    private ExpertiseService expertise;

    @Autowired
    private JdbcTemplate jdbc;

    private String slug;
    private String user;
    private long communityId;

    @BeforeEach
    void seedCommunityAndMember() {

        slug = "orphaned-evidence-" + FIXTURES.incrementAndGet();
        user = "orphaned-evidence-expert-" + FIXTURES.get();
        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES (?, 'Community of Practice', now(), now(), 0) ON CONFLICT DO NOTHING", ORG);
        communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, description, status, kind_id, created_at, "
                        + "updated_at, version) VALUES (?, ?, 'Orphaned Evidence', '', 'ACTIVE', "
                        + "(SELECT min(id) FROM community_kind WHERE organization_id = ?), now(), now(), 0) RETURNING id",
                Long.class, ORG, slug, ORG);
        jdbc.update("INSERT INTO identity_user (organization_id, user_id, display_name, email, active, scim_id, "
                + "arrival, created_at, updated_at, version) VALUES (?, ?, 'Orphan Expert', null, true, "
                + "?, 'SYNCED', now(), now(), 0)", ORG, user, "scim-" + user);
        jdbc.update("INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                + "version) VALUES (?, ?, ?, 'MEMBER', now(), now(), 0)", ORG, communityId, user);

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(user).claim("org", ORG).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void answersWhenTheArtifactBehindTheEvidenceIsGone() {

        signalFrom(SourceType.ARTIFACT, "CONTRIBUTED");

        assertThat(expertise.whoKnows(slug, TAG, false)).singleElement()
                .satisfies(p -> assertThat(p.evidence().recent()).singleElement()
                        .satisfies(item -> assertThat(item.label()).isNull()));
    }

    @Test
    void answersWhenTheTopicBehindTheEvidenceIsGone() {

        signalFrom(SourceType.TOPIC, "OFFERED");

        assertThat(expertise.whoKnows(slug, TAG, false)).singleElement()
                .satisfies(p -> assertThat(p.evidence().recent()).singleElement()
                        .satisfies(item -> assertThat(item.label()).isNull()));
    }

    @Test
    void answersWhenTheSlotBehindTheEvidenceIsGone() {

        signalFrom(SourceType.SLOT, "PRESENTED");

        assertThat(expertise.whoKnows(slug, TAG, false)).singleElement()
                .satisfies(p -> assertThat(p.evidence().recent()).singleElement()
                        .satisfies(item -> assertThat(item.label()).isNull()));
    }

    private void signalFrom(SourceType sourceType, String signalType) {

        jdbc.update(
                "INSERT INTO expertise_signal (organization_id, community_id, user_id, tag, signal_type, "
                        + "source_type, source_id, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                ORG, communityId, user, TAG, signalType, sourceType.name(), GONE, Timestamp.from(Instant.now()));
    }
}
