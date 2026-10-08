package dev.axonia.expertise.internal;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.expertise.CommunityAccess;
import dev.axonia.expertise.Declaration;
import dev.axonia.expertise.ExpertiseCurationService;
import dev.axonia.expertise.SignalType;
import dev.axonia.expertise.SourceType;
import dev.axonia.expertise.fake.FakeNamedAreaRepository;
import dev.axonia.expertise.internal.SignalWriter.SignalRow;
import dev.axonia.expertise.internal.SignalWriter.SignalSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A curator's merges and splits, read back through search and the member's own hide and unhide. */
class CurationPrivacyTest extends ExpertiseJdbcTestSupport {

    private static final String ORG = "default";
    private static final String SLUG = "curation-privacy";
    private static final String OTHER_SLUG = "curation-privacy-other";
    private static final String CURATOR = "privacy-curator";
    private static final String BOB = "privacy-bob";
    private static final String DANA = "privacy-dana";
    private static final Instant AT = Instant.parse("2026-09-01T12:00:00Z");
    private static final AtomicLong SOURCES = new AtomicLong(990_000L);

    @Autowired
    private SignalWriter signalWriter;
    @Autowired
    private ExpertiseQueryRepositoryImpl queries;
    @Autowired
    private TagAliasRepositoryImpl aliases;
    @Autowired
    private TagDismissalRepositoryImpl dismissals;
    @Autowired
    private DeclarationRepositoryImpl declarations;
    @Autowired
    private VocabularyLockImpl vocabularyLock;
    @Autowired
    private PreferenceRepositoryImpl preferences;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private long community;
    private long other;
    private ExpertiseCurationService curation;

    @BeforeEach
    void seedBobWithKubernetesEvidenceUnderAMergedSpelling() {

        community = seedCommunity(SLUG);
        other = seedCommunity(OTHER_SLUG);
        seedUser(BOB);
        seedMember(community, BOB);
        seedMember(other, BOB);
        var communityAccess = mock(CommunityAccess.class);
        when(communityAccess.communityIdForSlug(SLUG)).thenReturn(community);
        when(communityAccess.canCurateTags(community, CURATOR)).thenReturn(true);
        curation = new ExpertiseCurationService(() -> ORG,
                new CurationScope(() -> CURATOR, communityAccess, queries, declarations, aliases, List.of()), queries,
                aliases, dismissals, List.of(), signalWriter, declarations, new FakeNamedAreaRepository(),
                vocabularyLock);

        signal(community, CURATOR, "kubernetes");
        signal(community, CURATOR, "containers");
        signal(community, BOB, "k8s");
        declare(community, BOB, "k8s");
        aliases.addAlias(ORG, community, "k8s", "kubernetes");
        preferences.hideTag(ORG, BOB, "kubernetes");
    }

    @Test
    void afterAnUnmergeAndARemergeOneUnhideOfTheAreaMakesTheMemberFindableAgain() {

        // when
        curation.split(SLUG, "k8s");

        // then
        assertHiddenFromBoth(community, BOB, "k8s");

        // when
        curation.merge(SLUG, "k8s", "kubernetes");

        // then
        assertHiddenFromBoth(community, BOB, "kubernetes", "k8s");

        // when
        preferences.unhideTag(ORG, BOB, "kubernetes");

        // then
        assertThat(preferences.hiddenTags(ORG, BOB)).isEmpty();
        assertFindableThroughBoth(community, BOB, "kubernetes", "k8s");
    }

    @Test
    void movingAVariantOutOfAHiddenAreaHidesTheAreaItJoinsUntilThatIsUnhidden() {

        // when
        curation.merge(SLUG, "k8s", "containers");

        // then
        assertThat(preferences.hiddenTags(ORG, BOB)).containsExactly("containers");
        assertHiddenFromBoth(community, BOB, "containers", "k8s");

        // when
        preferences.unhideTag(ORG, BOB, "containers");

        // then
        assertFindableThroughBoth(community, BOB, "containers", "k8s");
    }

    @Test
    void movingAVariantOutKeepsTheOldAreaHiddenWhileOtherRowsStillReadAsIt() {

        // given
        signal(community, BOB, "kubernetes");

        // when
        curation.merge(SLUG, "k8s", "containers");

        // then
        assertThat(preferences.hiddenTags(ORG, BOB)).containsExactlyInAnyOrder("kubernetes", "containers");
        assertThat(findableBySignal(community, BOB, "kubernetes")).isFalse();
        assertHiddenFromBoth(community, BOB, "containers", "k8s");

        // when
        preferences.unhideTag(ORG, BOB, "containers");

        // then
        assertFindableThroughBoth(community, BOB, "containers", "k8s");
        assertThat(findableBySignal(community, BOB, "kubernetes")).isFalse();
    }

    @Test
    void aMergeKeepsTheVariantRowWhileAnotherCommunityReadsTheSpellingAsItsOwnArea() {

        // given - the split leaves bob's hide on "k8s", which he also signalled in a community that does not merge it
        curation.split(SLUG, "k8s");
        signal(other, BOB, "k8s");

        // when
        curation.merge(SLUG, "k8s", "kubernetes");

        // then
        assertThat(preferences.hiddenTags(ORG, BOB)).containsExactlyInAnyOrder("kubernetes", "k8s");
        assertThat(findableBySignal(other, BOB, "k8s")).isFalse();
        assertHiddenFromBoth(community, BOB, "kubernetes", "k8s");

        // when
        preferences.unhideTag(ORG, BOB, "k8s");

        // then
        assertThat(findableBySignal(other, BOB, "k8s")).isTrue();
        assertHiddenFromBoth(community, BOB, "kubernetes", "k8s");

        // when
        preferences.unhideTag(ORG, BOB, "kubernetes");

        // then
        assertFindableThroughBoth(community, BOB, "kubernetes", "k8s");
    }

    @Test
    void renamingASplitSpellingLeavesTheMemberFindableWhereAnotherCommunityReadsItAsAnotherArea() {

        // given - the other community reads bob's "k8s" as "containers", which he never hid
        aliases.addAlias(ORG, other, "k8s", "containers");
        signal(other, BOB, "k8s");
        declare(other, BOB, "k8s");
        curation.split(SLUG, "k8s");

        // when
        curation.rename(SLUG, "k8s", "kube");

        // then
        assertThat(preferences.hiddenTags(ORG, BOB)).containsExactly("kube").isSubsetOf(areasOf(BOB));
        assertFindableThroughBoth(other, BOB, "containers", "k8s");
        assertHiddenFromBoth(community, BOB, "kube");

        // when
        preferences.unhideTag(ORG, BOB, "kube");

        // then
        assertFindableThroughBoth(community, BOB, "kube");
    }

    @Test
    void deletingASplitSpellingLeavesTheMemberFindableWhereAnotherCommunityReadsItAsAnotherArea() {

        // given
        aliases.addAlias(ORG, other, "k8s", "containers");
        signal(other, BOB, "k8s");
        declare(other, BOB, "k8s");
        curation.split(SLUG, "k8s");

        // when
        curation.delete(SLUG, "k8s");

        // then
        assertThat(preferences.hiddenTags(ORG, BOB)).isEmpty();
        assertFindableThroughBoth(other, BOB, "containers", "k8s");
    }

    @Test
    void aMergeFoldsTheHiddenAreaOfAMemberWhoOnlyDeclaredTheVariant() {

        // given - dana declared "helm" without any evidence, and hid it
        seedUser(DANA);
        seedMember(community, DANA);
        signal(community, CURATOR, "helm");
        declare(community, DANA, "helm");
        preferences.hideTag(ORG, DANA, "helm");

        // when
        curation.merge(SLUG, "helm", "kubernetes");

        // then
        assertThat(preferences.hiddenTags(ORG, DANA)).containsExactly("kubernetes");
        assertThat(findableByDeclaration(community, DANA, "kubernetes", "helm")).isFalse();

        // when
        preferences.unhideTag(ORG, DANA, "kubernetes");

        // then
        assertThat(findableByDeclaration(community, DANA, "kubernetes", "helm")).isTrue();
    }

    @Test
    void aTagOnlyADeclarationCarriesIsStillTheCommunitysToRename() {

        // given - dana declared "helm" here, and nothing was ever signalled under it
        seedMemberUser(DANA);
        declare(community, DANA, "helm");

        // when
        curation.rename(SLUG, "helm", "helm-charts");

        // then
        assertThat(findableByDeclaration(community, DANA, "helm-charts")).isTrue();
        assertThat(findableByDeclaration(community, DANA, "helm")).isFalse();
    }

    @Test
    void aSplitCarriesTheHiddenAreaOfAMemberWhoOnlyDeclaredTheVariant() {

        // given - dana declared "helm", merged into the "kubernetes" area she hid, without any evidence
        seedMemberUser(DANA);
        signal(community, CURATOR, "helm");
        aliases.addAlias(ORG, community, "helm", "kubernetes");
        declare(community, DANA, "helm");
        preferences.hideTag(ORG, DANA, "kubernetes");

        // when
        curation.split(SLUG, "helm");

        // then
        assertThat(preferences.hiddenTags(ORG, DANA)).containsExactly("helm");
        assertThat(findableByDeclaration(community, DANA, "helm")).isFalse();

        // when
        preferences.unhideTag(ORG, DANA, "helm");

        // then
        assertThat(findableByDeclaration(community, DANA, "helm")).isTrue();
    }

    @Test
    void aDismissalStillHoldsWhenCreditReturnsAfterARenameMadeWhileItWasAway() {

        // given - bob dismissed his "kafka" signal, then credit for the source moved to dana
        var source = source();
        seedMemberUser(DANA);
        credit(source, BOB, "kafka");
        preferences.dismissSignal(ORG, BOB, new PreferenceRepository.SignalRef(SourceType.ARTIFACT, source.sourceId()),
                "kafka");
        credit(source, DANA, "kafka");

        // when
        curation.rename(SLUG, "kafka", "streaming");
        credit(source, BOB, "streaming");

        // then
        assertThat(findableBySignal(community, BOB, "streaming")).isFalse();
    }

    @Test
    void aDismissalStillHoldsWhenCreditReturnsAfterASplitMadeWhileItWasAway() {

        // given - bob dismissed his "kafak" signal under the area it read as, then credit moved to dana
        var source = source();
        seedMemberUser(DANA);
        aliases.addAlias(ORG, community, "kafak", "kafka");
        credit(source, BOB, "kafak");
        preferences.dismissSignal(ORG, BOB, new PreferenceRepository.SignalRef(SourceType.ARTIFACT, source.sourceId()),
                "kafka");
        credit(source, DANA, "kafak");

        // when
        curation.split(SLUG, "kafak");
        credit(source, BOB, "kafak");

        // then
        assertThat(findableBySignal(community, BOB, "kafak")).isFalse();
    }

    @Test
    void aDeleteMadeWhileCreditIsAwayDropsTheDismissal() {

        // given
        var source = source();
        seedMemberUser(DANA);
        credit(source, BOB, "kafka");
        preferences.dismissSignal(ORG, BOB, new PreferenceRepository.SignalRef(SourceType.ARTIFACT, source.sourceId()),
                "kafka");
        credit(source, DANA, "kafka");

        // when
        curation.delete(SLUG, "kafka");

        // then
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM expertise_dismissed_signal WHERE organization_id = :org "
                        + "AND user_id = :userId AND source_id = :sourceId",
                Map.of("org", ORG, "userId", BOB, "sourceId", source.sourceId()), Integer.class)).isZero();
    }

    private SignalSource source() {

        return new SignalSource(ORG, community, SourceType.ARTIFACT, SOURCES.incrementAndGet());
    }

    private void credit(SignalSource source, String userId, String tag) {

        signalWriter.replaceSource(source, List.of(new SignalRow(userId, tag, SignalType.CONTRIBUTED, AT)));
    }

    private void seedMemberUser(String userId) {

        seedUser(userId);
        seedMember(community, userId);
    }

    private void assertFindableThroughBoth(long communityId, String userId, String... tags) {

        assertThat(findableBySignal(communityId, userId, tags)).as("by signal").isTrue();
        assertThat(findableByDeclaration(communityId, userId, tags)).as("by declaration").isTrue();
    }

    private void assertHiddenFromBoth(long communityId, String userId, String... tags) {

        assertThat(findableBySignal(communityId, userId, tags)).as("by signal").isFalse();
        assertThat(findableByDeclaration(communityId, userId, tags)).as("by declaration").isFalse();
    }

    private boolean findableBySignal(long communityId, String userId, String... tags) {

        return queries.findPractitioners(ORG, communityId, Set.of(tags)).stream()
                .anyMatch(u -> u.userId().equals(userId));
    }

    private boolean findableByDeclaration(long communityId, String userId, String... tags) {

        return declarations.visibleIn(ORG, communityId, Set.of(tags)).stream().anyMatch(d -> d.userId().equals(userId));
    }

    /** The areas the member's own page shows: every signal and declaration, read through its community's aliases. */
    private Set<String> areasOf(String userId) {

        var areas = new HashSet<String>();
        queries.findForUser(ORG, userId).stream().flatMap(u -> u.signals().stream()).forEach(signal -> areas
                .add(AliasResolver.canonicalOf(signal.tag(), aliases.aliasMap(ORG, signal.communityId()))));
        declarations.forUser(ORG, userId).forEach(declaration -> areas
                .add(AliasResolver.canonicalOf(declaration.tag(), aliases.aliasMap(ORG, declaration.communityId()))));
        return areas;
    }

    private void declare(long communityId, String userId, String tag) {

        declarations.insert(ORG,
                Declaration.builder().communityId(communityId).userId(userId).tag(tag).declaredAt(AT).build());
    }

    private void signal(long communityId, String userId, String tag) {

        signalWriter.replaceSource(new SignalSource(ORG, communityId, SourceType.ARTIFACT, SOURCES.incrementAndGet()),
                List.of(new SignalRow(userId, tag, SignalType.CONTRIBUTED, AT)));
    }

    /** Seeds its own kind by label, never {@code min(id)}: the baseline seeds no reference catalog. */
    private long seedCommunity(String slug) {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES (:org, 'Privacy Guild', now(), now(), 0) ON CONFLICT DO NOTHING", Map.of("org", ORG));
        var kindId = jdbc.queryForObject(
                "SELECT id FROM community_kind WHERE organization_id = :org AND label = 'Privacy Guild'",
                Map.of("org", ORG), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, "
                        + "updated_at, version) VALUES (:org, :slug, :slug, :kindId, '', 'ACTIVE', now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", ORG, "slug", slug, "kindId", kindId), Long.class);
    }

    private void seedUser(String userId) {

        jdbc.update("""
                INSERT INTO identity_user (organization_id, user_id, display_name, email, active, scim_id, arrival,
                                           created_at, updated_at, version)
                VALUES (:org, :userId, :userId, :userId || '@example.test', true, 'scim-' || :userId, 'SYNCED',
                        '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', 0)
                """, new MapSqlParameterSource("org", ORG).addValue("userId", userId));
    }

    private void seedMember(long communityId, String userId) {

        jdbc.update(
                "INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                        + "version) VALUES (:org, :community, :userId, 'MEMBER', now(), now(), 0)",
                Map.of("org", ORG, "community", communityId, "userId", userId));
    }
}
