package dev.axonia;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
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

import dev.axonia.events.AddSlotRequest;
import dev.axonia.events.CreateOneOffRequest;
import dev.axonia.events.ProposeTopicRequest;
import dev.axonia.events.ScheduledAt;
import dev.axonia.events.SectionTemplate;
import dev.axonia.events.SessionService;
import dev.axonia.events.SlotService;
import dev.axonia.events.TopicKind;
import dev.axonia.events.TopicService;
import dev.axonia.expertise.ExpertiseCurationService;
import dev.axonia.expertise.TagUsageReport;
import dev.axonia.knowledge.ArtifactService;
import dev.axonia.knowledge.CreateArtifactRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One tag, one name, in every store that holds one.
 *
 * <p>
 * The three content stores used to disagree on how to spell a tag: the library lowercased it, the events context kept
 * the caller's capitals, and only the expertise projection slugified. Curation reads its vocabulary off the projection,
 * so it named {@code spring-boot} and compared it to content spelled {@code spring boot} or {@code Spring Boot}: usage
 * reported nothing, a delete wiped the projection while leaving the content tagged, and the tag resurrected on the next
 * edit. The writer tests were blind to it because they inserted pre-slugged literals straight into the tag tables.
 *
 * <p>
 * So this test never inserts a tag. It types {@code Spring Boot} into the three real write paths a member actually
 * uses, then drives the curator's three real verbs and asserts they reach the content.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class TagIdentityIntegrationTest {

    private static final String ORG = "default";
    private static final String CURATOR = "tag-identity-curator";

    /** How a member types it. */
    private static final String TYPED = "Spring Boot";

    /** The one name every store must hold it under. */
    private static final String SLUG = "spring-boot";

    /** A slug of its own per test: the fixtures commit, and are only truncated once the class is done. */
    private static final AtomicInteger COMMUNITIES = new AtomicInteger();

    @Autowired
    private ArtifactService artifacts;

    @Autowired
    private TopicService topics;

    @Autowired
    private SessionService sessions;

    @Autowired
    private SlotService slots;

    @Autowired
    private ExpertiseCurationService curation;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void authenticate() {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES (?, 'Community of Practice', now(), now(), 0) ON CONFLICT DO NOTHING", ORG);
        jdbc.update("INSERT INTO artifact_type (organization_id, label, created_at, updated_at, version) "
                + "VALUES (?, 'Notes', now(), now(), 0) ON CONFLICT DO NOTHING", ORG);
        jdbc.update(
                "INSERT INTO event_section_type (organization_id, label, expects_presenter, created_at, "
                        + "updated_at, version) VALUES (?, 'Deep Dive', true, now(), now(), 0) ON CONFLICT DO NOTHING",
                ORG);
        jdbc.update("INSERT INTO identity_user (organization_id, user_id, display_name, email, active, scim_id, "
                + "arrival, created_at, updated_at, version) VALUES (?, ?, 'Tag Curator', null, true, "
                + "'scim-tag-curator', 'SYNCED', now(), now(), 0) ON CONFLICT DO NOTHING", ORG, CURATOR);
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(CURATOR).claim("org", ORG).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void everyWritePathStoresTheTagUnderTheOneNameTheProjectionKnowsItBy() {

        var slug = communityTaggedThroughEveryWritePath();

        assertThat(contentTags(slug)).containsExactly(SLUG, SLUG, SLUG);
        assertThat(projectionTags(slug)).containsOnly(SLUG);
    }

    @Test
    void usageFindsTheContentACuratorIsAboutToTouch() {

        var slug = communityTaggedThroughEveryWritePath();

        assertThat(curation.usage(slug, TYPED)).isEqualTo(new TagUsageReport(1, 1, 1, 0, 0));
    }

    @Test
    void renamingRewritesTheContentAndNotJustTheProjection() {

        var slug = communityTaggedThroughEveryWritePath();

        curation.rename(slug, TYPED, "Spring Framework");

        assertThat(contentTags(slug)).containsExactly("spring-framework", "spring-framework", "spring-framework");
        assertThat(projectionTags(slug)).containsOnly("spring-framework");
        assertThat(searchDocumentOfTheArtifact(slug)).contains("spring-framework").doesNotContain(SLUG);
    }

    @Test
    void deletingStripsTheTagFromTheContentSoItCannotResurrect() {

        var slug = communityTaggedThroughEveryWritePath();

        curation.delete(slug, TYPED);

        assertThat(contentTags(slug)).isEmpty();
        assertThat(projectionTags(slug)).isEmpty();
        assertThat(searchDocumentOfTheArtifact(slug)).doesNotContain(SLUG);
    }

    /**
     * A community whose artifact, topic and talk slot were each tagged {@code Spring Boot} through the service a member
     * really goes through, so what lands in the three tag tables is what the product would have stored.
     */
    private String communityTaggedThroughEveryWritePath() {

        var slug = "tag-identity-" + COMMUNITIES.incrementAndGet();
        organizerOf(slug);

        artifacts.capture(slug, new CreateArtifactRequest("Resilience patterns", anArtifactType(),
                "https://example.test/spring", "Retries and timeouts", Set.of(TYPED), null, null));
        topics.propose(slug,
                new ProposeTopicRequest(TopicKind.OFFER, "Spring Boot in anger", "", List.of(TYPED), null));

        var session = sessions.createOneOff(slug,
                new CreateOneOffRequest("Meetup",
                        new ScheduledAt(LocalDate.of(2026, 6, 17), LocalTime.of(18, 0), "UTC"), null, 60,
                        List.of(new SectionTemplate(aSectionType(), "Talks", 30))));
        var sectionId = session.sections().get(0).id();
        slots.addSlot(session.id(), sectionId,
                new AddSlotRequest("Spring Boot in prod", null, 15, null, List.of(TYPED)));

        return slug;
    }

    /** Organizer, because curating tags and shaping a session both ask for it; member, because contributing does. */
    private void organizerOf(String slug) {

        var communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, description, status, kind_id, created_at, "
                        + "updated_at, version) VALUES (?, ?, ?, '', 'ACTIVE', "
                        + "(SELECT min(id) FROM community_kind WHERE organization_id = ?), now(), now(), 0) "
                        + "RETURNING id",
                Long.class, ORG, slug, slug, ORG);
        jdbc.update("INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                + "version) VALUES (?, ?, ?, 'ORGANIZER', now(), now(), 0)", ORG, communityId, CURATOR);
    }

    private Long anArtifactType() {

        return jdbc.queryForObject("SELECT min(id) FROM artifact_type WHERE organization_id = ?", Long.class, ORG);
    }

    private Long aSectionType() {

        return jdbc.queryForObject("SELECT min(id) FROM event_section_type WHERE organization_id = ?", Long.class, ORG);
    }

    /** Every tag the community's content carries, one row per store: artifact, topic, talk slot. */
    private List<String> contentTags(String slug) {

        return jdbc.queryForList("""
                SELECT t.tag FROM artifact_tag t JOIN artifact a ON a.id = t.artifact_id
                 JOIN communities c ON c.id = a.community_id WHERE c.slug = ?
                UNION ALL
                SELECT t.tag FROM topic_tag t JOIN topic p ON p.id = t.topic_id
                 JOIN communities c ON c.id = p.community_id WHERE c.slug = ?
                UNION ALL
                SELECT t.tag FROM event_slot_tag t JOIN event_slot s ON s.id = t.slot_id
                 JOIN event_section sec ON sec.id = s.section_id
                 JOIN community_session cs ON cs.id = sec.session_id
                 JOIN communities c ON c.id = cs.community_id WHERE c.slug = ?
                """, String.class, slug, slug, slug);
    }

    private List<String> projectionTags(String slug) {

        return jdbc.queryForList("""
                SELECT DISTINCT s.tag FROM expertise_signal s
                  JOIN communities c ON c.id = s.community_id
                 WHERE c.slug = ?
                """, String.class, slug);
    }

    private String searchDocumentOfTheArtifact(String slug) {

        return jdbc.queryForObject("""
                SELECT a.search_document FROM artifact a
                  JOIN communities c ON c.id = a.community_id
                 WHERE c.slug = ?
                """, String.class, slug);
    }
}
