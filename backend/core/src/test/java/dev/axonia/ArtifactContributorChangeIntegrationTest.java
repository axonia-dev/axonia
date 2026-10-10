package dev.axonia;

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

import dev.axonia.expertise.application.ExpertisePreferenceService;
import dev.axonia.expertise.application.ExpertiseService;
import dev.axonia.expertise.application.Practitioner;
import dev.axonia.expertise.domain.SourceType;
import dev.axonia.knowledge.api.CreateArtifactRequest;
import dev.axonia.knowledge.application.ArtifactCreditService;
import dev.axonia.knowledge.application.ArtifactService;
import dev.axonia.knowledge.application.ChangeContributorRequest;
import dev.axonia.knowledge.application.UpdateArtifactRequest;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A corrected contributor is a corrected expertise graph.
 *
 * <p>
 * The recording an organizer filed under their own name made them a Contributor in its subject. Changing the
 * contributor must take that away from the organizer and give it to the person whose work it is, through the real write
 * path and the real projection, with nothing inserted into {@code expertise_signal} by hand.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class ArtifactContributorChangeIntegrationTest {

    private static final String ORG = "default";
    private static final String ORGANIZER = "credit-change-organizer";
    private static final String SPEAKER = "credit-change-speaker";
    private static final String TAG = "kafka";

    /** A slug of its own per test: the fixtures commit, and are only truncated once the class is done. */
    private static final AtomicInteger COMMUNITIES = new AtomicInteger();

    @Autowired
    private ArtifactService artifacts;

    @Autowired
    private ArtifactCreditService credit;

    @Autowired
    private ExpertiseService expertise;

    @Autowired
    private ExpertisePreferenceService preferences;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void seedAndSignIn() {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES (?, 'Community of Practice', now(), now(), 0) ON CONFLICT DO NOTHING", ORG);
        jdbc.update("INSERT INTO artifact_type (organization_id, label, created_at, updated_at, version) "
                + "VALUES (?, 'Recording', now(), now(), 0) ON CONFLICT DO NOTHING", ORG);
        person(ORGANIZER, "Olive Organizer");
        person(SPEAKER, "Sam Speaker");
        signIn(ORGANIZER);
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void theGraphMovesFromTheFilerToThePersonWhoseWorkItIs() {

        var slug = communityWithASpeaker();
        var artifact = artifacts.capture(slug, recording());
        assertThat(signalOwners(artifact.id())).containsOnly(ORGANIZER);

        credit.changeContributor(artifact.id(), new ChangeContributorRequest(SPEAKER));

        assertThat(signalOwners(artifact.id())).containsOnly(SPEAKER);
    }

    @Test
    void anArchivedArtifactProjectsTheCorrectedContributorOnlyOnceRestored() {

        var slug = communityWithASpeaker();
        var artifact = artifacts.capture(slug, recording());
        artifacts.archive(artifact.id());

        credit.changeContributor(artifact.id(), new ChangeContributorRequest(SPEAKER));
        assertThat(signalOwners(artifact.id())).isEmpty();

        artifacts.restore(artifact.id());
        assertThat(signalOwners(artifact.id())).containsOnly(SPEAKER);
    }

    @Test
    void editAndDeleteRightsFollowTheNewContributor() {

        var slug = communityWithASpeaker();
        var artifact = artifacts.capture(slug, recording());
        signIn(SPEAKER);
        assertThatThrownBy(() -> artifacts.edit(artifact.id(), revision())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> artifacts.remove(artifact.id())).isInstanceOf(ForbiddenException.class);

        signIn(ORGANIZER);
        credit.changeContributor(artifact.id(), new ChangeContributorRequest(SPEAKER));
        signIn(SPEAKER);

        assertThat(artifacts.edit(artifact.id(), revision()).title()).isEqualTo("Kafka in production, revised");
        artifacts.remove(artifact.id());
        assertThatThrownBy(() -> artifacts.get(artifact.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void thePreviousContributorLosesEditAndDeleteRights() {

        var slug = communityWithASpeaker();
        var artifact = artifacts.capture(slug, recording());
        credit.changeContributor(artifact.id(), new ChangeContributorRequest(SPEAKER));
        credit.changeContributor(artifact.id(), new ChangeContributorRequest(ORGANIZER));
        signIn(SPEAKER);

        assertThatThrownBy(() -> artifacts.edit(artifact.id(), revision())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> artifacts.remove(artifact.id())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void anEditCarryingAContributorLeavesTheContributorUnchanged() {

        var slug = communityWithASpeaker();
        var artifact = artifacts.capture(slug, recording());
        var edit = jsonMapper.readValue("""
                {"title": "Kafka in production, revised", "typeId": %d, "url": "https://example.test/kafka",
                 "tags": ["%s"], "contributor": "%s", "contributorUserId": "%s"}
                """.formatted(anArtifactType(), TAG, SPEAKER, SPEAKER), UpdateArtifactRequest.class);

        assertThat(artifacts.edit(artifact.id(), edit).contributorUserId()).isEqualTo(ORGANIZER);
        assertThat(signalOwners(artifact.id())).containsOnly(ORGANIZER);
    }

    @Test
    void aSignalThePreviousContributorDismissedStaysDismissed() {

        var slug = communityWithASpeaker();
        var artifact = artifacts.capture(slug, recording());
        preferences.dismissSignal(SourceType.ARTIFACT, artifact.id(), TAG);
        assertThat(practitioners(slug)).isEmpty();

        credit.changeContributor(artifact.id(), new ChangeContributorRequest(SPEAKER));
        assertThat(practitioners(slug)).containsOnly(SPEAKER);

        credit.changeContributor(artifact.id(), new ChangeContributorRequest(ORGANIZER));
        assertThat(signalOwners(artifact.id())).containsOnly(ORGANIZER);
        assertThat(practitioners(slug)).isEmpty();
    }

    private CreateArtifactRequest recording() {

        return new CreateArtifactRequest("Kafka in production", anArtifactType(), "https://example.test/kafka", "",
                Set.of(TAG), null, null);
    }

    private UpdateArtifactRequest revision() {

        return new UpdateArtifactRequest("Kafka in production, revised", anArtifactType(), "https://example.test/kafka",
                "", Set.of(TAG));
    }

    private void signIn(String userId) {

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(userId).claim("org", ORG).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private void person(String userId, String displayName) {

        jdbc.update("INSERT INTO identity_user (organization_id, user_id, display_name, email, active, scim_id, "
                + "arrival, created_at, updated_at, version) VALUES (?, ?, ?, null, true, ?, 'SYNCED', now(), now(), 0) "
                + "ON CONFLICT DO NOTHING", ORG, userId, displayName, "scim-" + userId);
    }

    /** The organizer runs the community, so the closed Credit contributions axis still lets them credit. */
    private String communityWithASpeaker() {

        var slug = "credit-change-" + COMMUNITIES.incrementAndGet();
        var communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, description, status, kind_id, created_at, "
                        + "updated_at, version) VALUES (?, ?, ?, '', 'ACTIVE', "
                        + "(SELECT min(id) FROM community_kind WHERE organization_id = ?), now(), now(), 0) "
                        + "RETURNING id",
                Long.class, ORG, slug, slug, ORG);
        membership(communityId, ORGANIZER, "ORGANIZER");
        membership(communityId, SPEAKER, "MEMBER");
        return slug;
    }

    private void membership(Long communityId, String userId, String role) {

        jdbc.update("INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                + "version) VALUES (?, ?, ?, ?, now(), now(), 0)", ORG, communityId, userId, role);
    }

    private Long anArtifactType() {

        return jdbc.queryForObject("SELECT min(id) FROM artifact_type WHERE organization_id = ?", Long.class, ORG);
    }

    private List<String> signalOwners(Long artifactId) {

        return jdbc.queryForList(
                "SELECT DISTINCT user_id FROM expertise_signal "
                        + "WHERE organization_id = ? AND source_type = 'ARTIFACT' AND source_id = ?",
                String.class, ORG, artifactId);
    }

    private List<String> practitioners(String slug) {

        return expertise.whoKnows(slug, TAG, false).stream().map(Practitioner::userId).toList();
    }
}
