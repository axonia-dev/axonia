package dev.axonia.expertise.internal;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.expertise.Declaration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DeclarationRepositoryImpl}, as the Testcontainers owner (RLS bypassed by ownership;
 * {@code RlsIsolationTest} proves the policy). The baseline seeds nothing, so every test seeds the community, the
 * directory rows and the memberships it reads.
 */
class DeclarationRepositoryTest extends ExpertiseJdbcTestSupport {

    private static final String ORG = "default";
    private static final String OTHER_ORG = "other-org";
    private static final Instant EARLY = Instant.parse("2026-09-01T12:00:00Z");
    private static final Instant LATE = Instant.parse("2026-09-12T12:00:00Z");

    @Autowired
    private DeclarationRepositoryImpl repo;

    @Autowired
    private PreferenceRepository preferences;

    @Autowired
    private TagAliasRepository aliases;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void insertIsIdempotentAndKeepsTheFirstDeclaration() {

        var community = seedCommunity("platform");
        repo.insert(ORG, declaration(community, "alice", "kafka", EARLY));
        repo.insert(ORG, declaration(community, "alice", "kafka", LATE));

        assertThat(repo.inCommunity(ORG, community, "alice"))
                .containsExactly(declaration(community, "alice", "kafka", EARLY));
    }

    @Test
    void inCommunityIsPerPersonPerCommunityEarliestFirst() {

        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        repo.insert(ORG, declaration(platform, "alice", "terraform", LATE));
        repo.insert(ORG, declaration(platform, "alice", "kafka", EARLY));
        repo.insert(ORG, declaration(data, "alice", "spark", EARLY));
        repo.insert(ORG, declaration(platform, "bob", "kafka", EARLY));

        assertThat(repo.inCommunity(ORG, platform, "alice")).extracting(Declaration::tag).containsExactly("kafka",
                "terraform");
    }

    @Test
    void deleteRemovesOneDeclarationOnly() {

        var community = seedCommunity("platform");
        repo.insert(ORG, declaration(community, "alice", "kafka", EARLY));
        repo.insert(ORG, declaration(community, "alice", "terraform", EARLY));

        repo.delete(ORG, community, "alice", Set.of("kafka"));

        assertThat(repo.forUser(ORG, "alice")).extracting(Declaration::tag).containsExactly("terraform");
    }

    @Test
    void deleteRemovesEveryGivenSpellingAndLeavesTheRest() {

        var community = seedCommunity("platform");
        repo.insert(ORG, declaration(community, "alice", "k8s", EARLY));
        repo.insert(ORG, declaration(community, "alice", "kubernetes", EARLY));
        repo.insert(ORG, declaration(community, "alice", "terraform", EARLY));

        repo.delete(ORG, community, "alice", Set.of("kubernetes", "k8s"));

        assertThat(repo.forUser(ORG, "alice")).extracting(Declaration::tag).containsExactly("terraform");
    }

    @Test
    void deleteAllInLeavesTheSameMembersOtherCommunities() {

        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        repo.insert(ORG, declaration(platform, "alice", "kafka", EARLY));
        repo.insert(ORG, declaration(data, "alice", "spark", EARLY));

        repo.deleteAllIn(ORG, platform, "alice");

        assertThat(repo.forUser(ORG, "alice")).extracting(Declaration::tag).containsExactly("spark");
    }

    @Test
    void visibleInReturnsAnActiveMembersDeclarationsForTheRequestedTags() {

        var community = seedCommunity("platform");
        seedMember(community, "alice", true);
        repo.insert(ORG, declaration(community, "alice", "kafka", EARLY));
        repo.insert(ORG, declaration(community, "alice", "terraform", EARLY));

        assertThat(repo.visibleIn(ORG, community, Set.of("kafka", "apache-kafka")))
                .containsExactly(declaration(community, "alice", "kafka", EARLY));
    }

    @Test
    void visibleInExcludesAnOptedOutMemberAHiddenTagAnInactiveMemberAndANonMember() {

        var community = seedCommunity("platform");
        seedMember(community, "opted-out", true);
        seedMember(community, "hider", true);
        seedMember(community, "inactive", false);
        seedUser("departed", true);
        preferences.setOptOut(ORG, "opted-out", true);
        preferences.hideTag(ORG, "hider", "kafka");
        for (var userId : new String[]{"opted-out", "hider", "inactive", "departed"}) {
            repo.insert(ORG, declaration(community, userId, "kafka", EARLY));
        }

        assertThat(repo.visibleIn(ORG, community, Set.of("kafka"))).isEmpty();
    }

    @Test
    void visibleInExcludesADeclaredVariantWhoseAreaIsHidden() {

        var community = seedCommunity("platform");
        seedMember(community, "alice", true);
        aliases.addAlias(ORG, community, "k8s", "kubernetes");
        repo.insert(ORG, declaration(community, "alice", "k8s", EARLY));
        preferences.hideTag(ORG, "alice", "kubernetes");

        assertThat(repo.visibleIn(ORG, community, Set.of("kubernetes", "k8s"))).isEmpty();
    }

    @Test
    void visibleInKeepsADeclaredAreaWhenOnlyAVariantSpellingOfItIsHidden() {

        var community = seedCommunity("platform");
        seedMember(community, "alice", true);
        aliases.addAlias(ORG, community, "k8s", "kubernetes");
        repo.insert(ORG, declaration(community, "alice", "kubernetes", EARLY));
        preferences.hideTag(ORG, "alice", "k8s");

        assertThat(repo.visibleIn(ORG, community, Set.of("kubernetes", "k8s")))
                .containsExactly(declaration(community, "alice", "kubernetes", EARLY));
    }

    @Test
    void visibleInIgnoresAHiddenSpellingTheDeclarationsCommunityReadsAsAnotherArea() {

        var community = seedCommunity("platform");
        seedMember(community, "alice", true);
        aliases.addAlias(ORG, community, "k8s", "kubernetes");
        repo.insert(ORG, declaration(community, "alice", "k8s", EARLY));
        preferences.hideTag(ORG, "alice", "k8s");

        assertThat(repo.visibleIn(ORG, community, Set.of("kubernetes", "k8s")))
                .containsExactly(declaration(community, "alice", "k8s", EARLY));
    }

    @Test
    void visibleInReadsTheHiddenAreaThroughTheDeclarationsOwnCommunity() {

        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        seedMember(platform, "alice", true);
        aliases.addAlias(ORG, data, "k8s", "kubernetes");
        repo.insert(ORG, declaration(platform, "alice", "k8s", EARLY));
        preferences.hideTag(ORG, "alice", "kubernetes");

        assertThat(repo.visibleIn(ORG, platform, Set.of("k8s")))
                .containsExactly(declaration(platform, "alice", "k8s", EARLY));
    }

    @Test
    void visibleInIgnoresAnotherCommunitysDeclarations() {

        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        seedMember(data, "alice", true);
        repo.insert(ORG, declaration(data, "alice", "kafka", EARLY));

        assertThat(repo.visibleIn(ORG, platform, Set.of("kafka"))).isEmpty();
    }

    @Test
    void allVisibleInReturnsEveryTagAnActiveMemberDeclaredHere() {

        var community = seedCommunity("platform");
        seedMember(community, "alice", true);
        seedMember(community, "bob", true);
        repo.insert(ORG, declaration(community, "alice", "kafka", EARLY));
        repo.insert(ORG, declaration(community, "alice", "terraform", LATE));
        repo.insert(ORG, declaration(community, "bob", "kafka", LATE));

        assertThat(repo.allVisibleIn(ORG, community)).containsExactlyInAnyOrder(
                declaration(community, "alice", "kafka", EARLY), declaration(community, "alice", "terraform", LATE),
                declaration(community, "bob", "kafka", LATE));
    }

    @Test
    void allVisibleInExcludesAnOptedOutMemberAHiddenAreaAnInactiveMemberAndANonMember() {

        var community = seedCommunity("platform");
        seedMember(community, "opted-out", true);
        seedMember(community, "hider", true);
        seedMember(community, "inactive", false);
        seedUser("departed", true);
        preferences.setOptOut(ORG, "opted-out", true);
        aliases.addAlias(ORG, community, "k8s", "kubernetes");
        preferences.hideTag(ORG, "hider", "kubernetes");
        repo.insert(ORG, declaration(community, "opted-out", "kafka", EARLY));
        repo.insert(ORG, declaration(community, "hider", "k8s", EARLY));
        repo.insert(ORG, declaration(community, "inactive", "kafka", EARLY));
        repo.insert(ORG, declaration(community, "departed", "kafka", EARLY));

        assertThat(repo.allVisibleIn(ORG, community)).isEmpty();
    }

    @Test
    void allVisibleInIgnoresAnotherCommunityAndAnotherOrganization() {

        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        seedMember(platform, "alice", true);
        seedMembership(ORG, data, "alice");
        repo.insert(ORG, declaration(data, "alice", "spark", EARLY));
        seedForeignMember(platform, "frank");
        repo.insert(OTHER_ORG, declaration(platform, "frank", "kafka", EARLY));

        assertThat(repo.allVisibleIn(ORG, platform)).isEmpty();
    }

    @Test
    void tagsInReturnsEachDeclaredTagOnceForThisCommunityOnly() {

        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        repo.insert(ORG, declaration(platform, "alice", "kafka", EARLY));
        repo.insert(ORG, declaration(platform, "bob", "kafka", LATE));
        repo.insert(ORG, declaration(platform, "alice", "terraform", EARLY));
        repo.insert(ORG, declaration(data, "alice", "spark", EARLY));

        assertThat(repo.tagsIn(ORG, platform)).containsExactly("kafka", "terraform");
    }

    @Test
    void tagsInAndCountInAreBlindToNobodyFindableUnderTheTag() {

        // a curator curates the name, so what a delete would rewrite is counted whoever declared it
        var community = seedCommunity("platform");
        seedUser("inactive", false);
        repo.insert(ORG, declaration(community, "inactive", "kafka", EARLY));

        assertThat(repo.tagsIn(ORG, community)).containsExactly("kafka");
        assertThat(repo.countIn(ORG, community, Set.of("kafka"))).isEqualTo(1);
    }

    @Test
    void countInCountsThePeopleBehindEverySpellingGivenAndNothingElse() {

        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        repo.insert(ORG, declaration(platform, "alice", "k8s", EARLY));
        repo.insert(ORG, declaration(platform, "alice", "kubernetes", EARLY));
        repo.insert(ORG, declaration(platform, "bob", "k8s", LATE));
        repo.insert(ORG, declaration(platform, "alice", "terraform", EARLY));
        repo.insert(ORG, declaration(data, "alice", "k8s", EARLY));

        // alice typed two spellings of the one area, so the area has two people, not three declarations
        assertThat(repo.countIn(ORG, platform, Set.of("kubernetes", "k8s"))).isEqualTo(2);
    }

    @Test
    void countInOfNoSpellingIsZero() {

        var community = seedCommunity("platform");
        repo.insert(ORG, declaration(community, "alice", "kafka", EARLY));

        assertThat(repo.countIn(ORG, community, Set.of())).isZero();
    }

    @Test
    void renameTagRewritesThisCommunityOnly() {

        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        repo.insert(ORG, declaration(platform, "alice", "k8s", EARLY));
        repo.insert(ORG, declaration(data, "alice", "k8s", EARLY));

        repo.renameTag(ORG, platform, "k8s", "kubernetes");

        assertThat(repo.inCommunity(ORG, platform, "alice")).extracting(Declaration::tag).containsExactly("kubernetes");
        assertThat(repo.inCommunity(ORG, data, "alice")).extracting(Declaration::tag).containsExactly("k8s");
    }

    @Test
    void renameTagFoldsIntoAnExistingDeclarationKeepingTheEarlierDate() {

        var community = seedCommunity("platform");
        repo.insert(ORG, declaration(community, "alice", "k8s", EARLY));
        repo.insert(ORG, declaration(community, "alice", "kubernetes", LATE));

        repo.renameTag(ORG, community, "k8s", "kubernetes");

        assertThat(repo.forUser(ORG, "alice")).containsExactly(declaration(community, "alice", "kubernetes", EARLY));
    }

    @Test
    void renameTagLeavesAnotherMembersDeclarationOfTheTargetAlone() {

        var community = seedCommunity("platform");
        repo.insert(ORG, declaration(community, "alice", "k8s", EARLY));
        repo.insert(ORG, declaration(community, "bob", "kubernetes", LATE));

        repo.renameTag(ORG, community, "k8s", "kubernetes");

        assertThat(repo.inCommunity(ORG, community, "alice"))
                .containsExactly(declaration(community, "alice", "kubernetes", EARLY));
        assertThat(repo.inCommunity(ORG, community, "bob"))
                .containsExactly(declaration(community, "bob", "kubernetes", LATE));
    }

    @Test
    void renameTagFoldKeepsTheTargetsDateWhenItIsTheEarlierOne() {

        var community = seedCommunity("platform");
        repo.insert(ORG, declaration(community, "alice", "k8s", LATE));
        repo.insert(ORG, declaration(community, "alice", "kubernetes", EARLY));

        repo.renameTag(ORG, community, "k8s", "kubernetes");

        assertThat(repo.forUser(ORG, "alice")).containsExactly(declaration(community, "alice", "kubernetes", EARLY));
    }

    @Test
    void removeTagDropsThisCommunitysDeclarationsOnly() {

        var platform = seedCommunity("platform");
        var data = seedCommunity("data");
        repo.insert(ORG, declaration(platform, "alice", "misc", EARLY));
        repo.insert(ORG, declaration(data, "alice", "misc", EARLY));

        repo.removeTag(ORG, platform, "misc");

        assertThat(repo.forUser(ORG, "alice")).containsExactly(declaration(data, "alice", "misc", EARLY));
    }

    private static Declaration declaration(long communityId, String userId, String tag, Instant declaredAt) {

        return Declaration.builder().communityId(communityId).userId(userId).tag(tag).declaredAt(declaredAt).build();
    }

    /** Seeds its own kind by label, never {@code min(id)}: the baseline seeds no reference catalog. */
    private long seedCommunity(String slug) {

        jdbc.update(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                        + "VALUES (:org, 'Declaration Guild', now(), now(), 0) ON CONFLICT DO NOTHING",
                Map.of("org", ORG));
        var kindId = jdbc.queryForObject(
                "SELECT id FROM community_kind WHERE organization_id = :org AND label = 'Declaration Guild'",
                Map.of("org", ORG), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, "
                        + "updated_at, version) VALUES (:org, :slug, :slug, :kindId, '', 'ACTIVE', now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", ORG, "slug", slug, "kindId", kindId), Long.class);
    }

    private void seedMember(long communityId, String userId, boolean active) {

        seedUser(userId, active);
        seedMembership(ORG, communityId, userId);
    }

    /** A present, active member of another organization, in the same community row. */
    private void seedForeignMember(long communityId, String userId) {

        seedUser(OTHER_ORG, userId, true);
        seedMembership(OTHER_ORG, communityId, userId);
    }

    private void seedMembership(String org, long communityId, String userId) {

        jdbc.update(
                "INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                        + "version) VALUES (:org, :community, :userId, 'MEMBER', now(), now(), 0)",
                Map.of("org", org, "community", communityId, "userId", userId));
    }

    private void seedUser(String userId, boolean active) {

        seedUser(ORG, userId, active);
    }

    private void seedUser(String org, String userId, boolean active) {

        jdbc.update("""
                INSERT INTO identity_user (organization_id, user_id, display_name, email, active, scim_id, arrival,
                                           created_at, updated_at, version)
                VALUES (:org, :userId, :userId, :userId || '@example.test', :active, 'scim-' || :userId, 'SYNCED',
                        '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', 0)
                """, new MapSqlParameterSource("org", org).addValue("userId", userId).addValue("active", active));
    }
}
