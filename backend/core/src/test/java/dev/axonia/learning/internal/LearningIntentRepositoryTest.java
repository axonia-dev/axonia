package dev.axonia.learning.internal;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.learning.LearningIntent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration tests for {@link LearningIntentRepositoryImpl}, as the Testcontainers owner (RLS bypassed by ownership;
 * {@code RlsIsolationTest} proves the policy). The baseline seeds nothing, so every test seeds the community, the
 * directory rows and the memberships it reads.
 */
@Import(LearningIntentRepositoryImpl.class)
class LearningIntentRepositoryTest extends CoreJdbcTestSupport {

    private static final String ORG = "default";
    private static final Instant EARLY = Instant.parse("2026-09-01T12:00:00Z");
    private static final Instant LATE = Instant.parse("2026-09-12T12:00:00Z");

    @Autowired
    private LearningIntentRepositoryImpl repo;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void insertIsIdempotentAndKeepsTheFirstIntent() {

        var community = seedCommunity("platform");
        repo.insert(ORG, intent(community, "alice", "kafka", EARLY));
        repo.insert(ORG, intent(community, "alice", "kafka", LATE));

        assertThat(repo.inCommunity(ORG, community, "alice"))
                .containsExactly(intent(community, "alice", "kafka", EARLY));
    }

    @Test
    void inCommunityIsOneMembersIntentsThereEarliestFirst() {

        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        repo.insert(ORG, intent(platform, "alice", "terraform", LATE));
        repo.insert(ORG, intent(platform, "alice", "kafka", EARLY));
        repo.insert(ORG, intent(data, "alice", "spark", EARLY));
        repo.insert(ORG, intent(platform, "bob", "kafka", EARLY));

        assertThat(repo.inCommunity(ORG, platform, "alice")).extracting(LearningIntent::tag).containsExactly("kafka",
                "terraform");
    }

    @Test
    void deleteRemovesEveryGivenSpellingAndLeavesTheRest() {

        var community = seedCommunity("platform");
        repo.insert(ORG, intent(community, "alice", "k8s", EARLY));
        repo.insert(ORG, intent(community, "alice", "kubernetes", EARLY));
        repo.insert(ORG, intent(community, "alice", "terraform", EARLY));

        repo.delete(ORG, community, "alice", Set.of("kubernetes", "k8s"));

        assertThat(repo.inCommunity(ORG, community, "alice")).extracting(LearningIntent::tag)
                .containsExactly("terraform");
    }

    @Test
    void deleteAllInEmptiesOneMembersIntentsInOneCommunity() {

        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        repo.insert(ORG, intent(platform, "alice", "kafka", EARLY));
        repo.insert(ORG, intent(platform, "bob", "kafka", EARLY));
        repo.insert(ORG, intent(data, "alice", "spark", EARLY));

        repo.deleteAllIn(ORG, platform, "alice");

        assertThat(repo.inCommunity(ORG, platform, "alice")).isEmpty();
        assertThat(repo.inCommunity(ORG, platform, "bob")).hasSize(1);
        assertThat(repo.inCommunity(ORG, data, "alice")).hasSize(1);
    }

    @Test
    void currentInKeepsEverySpelling() {

        var community = seedCommunity("platform");
        seedMember(community, "alice", true);
        repo.insert(ORG, intent(community, "alice", "k8s", EARLY));
        repo.insert(ORG, intent(community, "alice", "kubernetes", LATE));

        assertThat(repo.currentIn(ORG, community)).extracting(LearningIntent::tag).containsExactly("k8s", "kubernetes");
    }

    @Test
    void currentInSkipsFormerAndInactiveMembers() {

        var community = seedCommunity("platform");
        seedMember(community, "alice", true);
        seedMember(community, "carol", false);
        seedUser("bob", true);
        repo.insert(ORG, intent(community, "alice", "kafka", EARLY));
        repo.insert(ORG, intent(community, "bob", "kafka", EARLY));
        repo.insert(ORG, intent(community, "carol", "kafka", EARLY));

        assertThat(repo.currentIn(ORG, community)).extracting(LearningIntent::userId).containsExactly("alice");
    }

    @Test
    void currentInIgnoresAnotherCommunitysIntents() {

        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        seedMember(platform, "alice", true);
        seedMembership(data, "alice");
        repo.insert(ORG, intent(data, "alice", "spark", EARLY));

        assertThat(repo.currentIn(ORG, platform)).isEmpty();
    }

    @Test
    void tagsInListsEveryTagOnceWhoeverHoldsIt() {

        var community = seedCommunity("platform");
        seedUser("inactive", false);
        repo.insert(ORG, intent(community, "alice", "kafka", EARLY));
        repo.insert(ORG, intent(community, "bob", "kafka", EARLY));
        repo.insert(ORG, intent(community, "inactive", "helm", EARLY));

        assertThat(repo.tagsIn(ORG, community)).containsExactly("helm", "kafka");
    }

    @Test
    void countInCountsMembersHoldingTheExactTag() {

        var community = seedCommunity("platform");
        repo.insert(ORG, intent(community, "alice", "kafka", EARLY));
        repo.insert(ORG, intent(community, "bob", "kafka", EARLY));
        repo.insert(ORG, intent(community, "bob", "helm", EARLY));

        assertThat(repo.countIn(ORG, community, "kafka")).isEqualTo(2);
        assertThat(repo.countIn(ORG, community, "rust")).isZero();
    }

    @Test
    void renameFoldsIntoAnExistingIntentKeepingTheEarlierDate() {

        var community = seedCommunity("platform");
        repo.insert(ORG, intent(community, "alice", "kubernetes", LATE));
        repo.insert(ORG, intent(community, "alice", "k8s", EARLY));
        repo.insert(ORG, intent(community, "bob", "k8s", LATE));

        repo.renameTag(ORG, community, "k8s", "kubernetes");

        assertThat(repo.inCommunity(ORG, community, "alice"))
                .containsExactly(intent(community, "alice", "kubernetes", EARLY));
        assertThat(repo.inCommunity(ORG, community, "bob"))
                .containsExactly(intent(community, "bob", "kubernetes", LATE));
    }

    @Test
    void renameFoldKeepsTheTargetsDateWhenItIsTheEarlierOne() {

        var community = seedCommunity("platform");
        repo.insert(ORG, intent(community, "alice", "k8s", LATE));
        repo.insert(ORG, intent(community, "alice", "kubernetes", EARLY));

        repo.renameTag(ORG, community, "k8s", "kubernetes");

        assertThat(repo.inCommunity(ORG, community, "alice"))
                .containsExactly(intent(community, "alice", "kubernetes", EARLY));
    }

    @Test
    void renameRewritesThisCommunityOnly() {

        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        repo.insert(ORG, intent(platform, "alice", "k8s", EARLY));
        repo.insert(ORG, intent(data, "alice", "k8s", EARLY));

        repo.renameTag(ORG, platform, "k8s", "kubernetes");

        assertThat(repo.tagsIn(ORG, platform)).containsExactly("kubernetes");
        assertThat(repo.tagsIn(ORG, data)).containsExactly("k8s");
    }

    @Test
    void removeTagDeletesItInOneCommunityOnly() {

        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        repo.insert(ORG, intent(platform, "alice", "kafka", EARLY));
        repo.insert(ORG, intent(data, "alice", "kafka", EARLY));

        repo.removeTag(ORG, platform, "kafka");

        assertThat(repo.tagsIn(ORG, platform)).isEmpty();
        assertThat(repo.tagsIn(ORG, data)).containsExactly("kafka");
    }

    @Test
    void lockLearnerIsReentrantInOneTransaction() {

        var community = seedCommunity("platform");

        assertThatCode(() -> {
            repo.lockLearner(ORG, community, "alice");
            repo.lockLearner(ORG, community, "alice");
        }).doesNotThrowAnyException();
    }

    private static LearningIntent intent(long community, String user, String tag, Instant at) {

        return LearningIntent.builder().communityId(community).userId(user).tag(tag).declaredAt(at).build();
    }

    /** Seeds its own kind by label, never {@code min(id)}: the baseline seeds no reference catalog. */
    private long seedCommunity(String slug) {

        jdbc.update(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                        + "VALUES (:org, 'Learning Guild', now(), now(), 0) ON CONFLICT DO NOTHING",
                Map.of("org", ORG));
        var kindId = jdbc.queryForObject(
                "SELECT id FROM community_kind WHERE organization_id = :org AND label = 'Learning Guild'",
                Map.of("org", ORG), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, "
                        + "updated_at, version) VALUES (:org, :slug, :slug, :kindId, '', 'ACTIVE', now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", ORG, "slug", slug, "kindId", kindId), Long.class);
    }

    private void seedMember(long communityId, String userId, boolean active) {

        seedUser(userId, active);
        seedMembership(communityId, userId);
    }

    private void seedMembership(long communityId, String userId) {

        jdbc.update(
                "INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                        + "version) VALUES (:org, :community, :userId, 'MEMBER', now(), now(), 0)",
                Map.of("org", ORG, "community", communityId, "userId", userId));
    }

    private void seedUser(String userId, boolean active) {

        jdbc.update("""
                INSERT INTO identity_user (organization_id, user_id, display_name, email, active, scim_id, arrival,
                                           created_at, updated_at, version)
                VALUES (:org, :userId, :userId, :userId || '@example.test', :active, 'scim-' || :userId, 'SYNCED',
                        '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', 0)
                """, new MapSqlParameterSource("org", ORG).addValue("userId", userId).addValue("active", active));
    }
}
