package dev.axonia.expertise.internal;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.expertise.ExpertiseSignal;
import dev.axonia.expertise.SignalType;
import dev.axonia.expertise.SourceType;
import dev.axonia.expertise.internal.SignalWriter.SignalSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ExpertiseQueryRepositoryImpl}.
 *
 * <p>
 * Runs as the Testcontainers superuser (table owner), which bypasses RLS — no {@code app.current_org} session variable
 * is needed. This matches the pattern used across all {@code JdbcTestSupport} integration tests in this codebase (see
 * {@link SignalWriterTest}).
 *
 * <p>
 * {@link ExpertiseQueryRepositoryImpl} and {@link SignalWriter} are plain {@code @Component}/{@code @Repository} beans,
 * not Spring Data repositories, so the {@code @DataJdbcTest} slice does not register them automatically — both are
 * imported via {@link ExpertiseJdbcTestSupport}.
 *
 * <p>
 * Signals are seeded via {@link SignalWriter} (normalises and deduplicates). Preference-table rows (opt_out,
 * hidden_tag, dismissed_signal) are seeded directly via {@link NamedParameterJdbcTemplate} since no write-side
 * component manages them yet.
 */
class ExpertiseQueryRepositoryTest extends ExpertiseJdbcTestSupport {

    @Autowired
    private ExpertiseQueryRepositoryImpl repo;

    @Autowired
    private SignalWriter writer;

    @Autowired
    private TagAliasRepository aliases;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private static final String ORG = "default";
    private static final Long COMMUNITY = 1L;
    private static final Instant OCCURRED = Instant.parse("2026-01-15T10:00:00Z");

    // -----------------------------------------------------------------------
    // findPractitioners — happy path
    // -----------------------------------------------------------------------

    @Test
    void findPractitioners_returnsPractitionerWithMatchingSignals() {

        // given — alice has two signals for kafka in community 1
        seedUser("alice", true);
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 1001L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.PRESENTED, OCCURRED)));
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 2001L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.CONTRIBUTED, OCCURRED)));

        // when
        var result = repo.findPractitioners(ORG, COMMUNITY, Set.of("kafka"));

        // then — exactly one UserSignals entry for alice with both signals
        assertThat(result).hasSize(1);
        var alice = result.get(0);
        assertThat(alice.userId()).isEqualTo("alice");
        assertThat(alice.signals()).hasSize(2);
        assertThat(alice.signals()).extracting(s -> s.tag()).containsOnly("kafka");
    }

    @Test
    void findPractitioners_returnsOnlySignalsMatchingRequestedTags() {

        // given — bob has kafka and spring-boot signals
        seedUser("bob", true);
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 1002L),
                List.of(new SignalWriter.SignalRow("bob", "kafka", SignalType.PRESENTED, OCCURRED),
                        new SignalWriter.SignalRow("bob", "spring-boot", SignalType.PRESENTED, OCCURRED)));

        // when — only ask for kafka
        var result = repo.findPractitioners(ORG, COMMUNITY, Set.of("kafka"));

        // then — bob's spring-boot signal is not included
        assertThat(result).hasSize(1);
        assertThat(result.get(0).signals()).extracting(s -> s.tag()).containsOnly("kafka");
    }

    @Test
    void findPractitioners_returnsEmptyForEmptyTagSet() {

        // given — alice has a signal, but we ask for no tags at all (e.g. a topic with no tags)
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 1009L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.PRESENTED, OCCURRED)));

        // when — an empty tag set must not build an invalid `IN ()` clause
        var result = repo.findPractitioners(ORG, COMMUNITY, Set.of());

        // then — nobody matches an empty topic, and no SQL error is thrown
        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // findPractitioners — exclusions
    // -----------------------------------------------------------------------

    @Test
    void findPractitioners_excludesOptedOutUser() {

        // given - carol is a present member with a kafka signal but has opted out
        seedUser("carol", true);
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 1003L),
                List.of(new SignalWriter.SignalRow("carol", "kafka", SignalType.PRESENTED, OCCURRED)));
        insertOptOut(ORG, "carol");

        // when
        var result = repo.findPractitioners(ORG, COMMUNITY, Set.of("kafka"));

        // then - carol is absent because of the opt-out, not because she is unseeded
        assertThat(result).extracting(ExpertiseQueryRepository.UserSignals::userId).doesNotContain("carol");
    }

    @Test
    void findPractitioners_excludesHiddenTag() {

        // given — dana has kafka and java signals; she has hidden kafka
        seedUser("dana", true);
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 1004L),
                List.of(new SignalWriter.SignalRow("dana", "kafka", SignalType.PRESENTED, OCCURRED),
                        new SignalWriter.SignalRow("dana", "java", SignalType.PRESENTED, OCCURRED)));
        insertHiddenTag(ORG, "dana", "kafka");

        // when
        var result = repo.findPractitioners(ORG, COMMUNITY, Set.of("kafka", "java"));

        // then — dana appears only with the java signal
        assertThat(result).hasSize(1);
        var dana = result.get(0);
        assertThat(dana.userId()).isEqualTo("dana");
        assertThat(dana.signals()).extracting(s -> s.tag()).containsOnly("java");
    }

    @Test
    void findPractitioners_excludesASignalUnderAVariantWhoseAreaIsHidden() {

        // given - "k8s" is merged into "kubernetes" here, and erin hid the area
        seedUser("erin", true);
        aliases.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        seedSignal("erin", "k8s");
        insertHiddenTag(ORG, "erin", "kubernetes");

        // when
        var result = repo.findPractitioners(ORG, COMMUNITY, Set.of("kubernetes", "k8s"));

        // then
        assertThat(result).extracting(ExpertiseQueryRepository.UserSignals::userId).doesNotContain("erin");
    }

    @Test
    void findPractitioners_keepsTheAreaWhenOnlyAVariantSpellingOfItIsHidden() {

        // given - hiding the spelling "k8s" leaves the signals stored as "kubernetes" visible
        seedUser("fred", true);
        aliases.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        seedSignal("fred", "kubernetes");
        insertHiddenTag(ORG, "fred", "k8s");

        // when
        var result = repo.findPractitioners(ORG, COMMUNITY, Set.of("kubernetes", "k8s"));

        // then
        assertThat(result).extracting(ExpertiseQueryRepository.UserSignals::userId).contains("fred");
    }

    @Test
    void findPractitioners_ignoresAHiddenSpellingTheSignalsCommunityReadsAsAnotherArea() {

        // given - "k8s" reads as "kubernetes" here, and only the spelling is hidden
        seedUser("hope", true);
        aliases.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        seedSignal("hope", "k8s");
        insertHiddenTag(ORG, "hope", "k8s");

        // when
        var result = repo.findPractitioners(ORG, COMMUNITY, Set.of("kubernetes", "k8s"));

        // then
        assertThat(result).extracting(ExpertiseQueryRepository.UserSignals::userId).contains("hope");
    }

    @Test
    void findPractitioners_readsTheHiddenAreaThroughTheSignalsOwnCommunity() {

        // given - only another community merges "k8s" into "kubernetes"
        seedUser("gina", true);
        aliases.addAlias(ORG, COMMUNITY + 1, "k8s", "kubernetes");
        seedSignal("gina", "k8s");
        insertHiddenTag(ORG, "gina", "kubernetes");

        // when
        var result = repo.findPractitioners(ORG, COMMUNITY, Set.of("k8s"));

        // then
        assertThat(result).extracting(ExpertiseQueryRepository.UserSignals::userId).contains("gina");
    }

    @Test
    void findPractitioners_excludesASignalUnderAVariantDismissedUnderItsArea() {

        // given - the member's page shows the "k8s" signal under "kubernetes", and dismisses it by that name
        seedUser("hana", true);
        aliases.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 1101L),
                List.of(new SignalWriter.SignalRow("hana", "k8s", SignalType.PRESENTED, OCCURRED)));
        insertDismissed(ORG, "hana", SourceType.SLOT, 1101L, "kubernetes");

        // when
        var result = repo.findPractitioners(ORG, COMMUNITY, Set.of("kubernetes", "k8s"));

        // then
        assertThat(result).extracting(ExpertiseQueryRepository.UserSignals::userId).doesNotContain("hana");
    }

    @Test
    void findPractitioners_excludesASignalDismissedUnderItsOwnSpellingWhileMerged() {

        // given
        seedUser("iris", true);
        aliases.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 1103L),
                List.of(new SignalWriter.SignalRow("iris", "k8s", SignalType.PRESENTED, OCCURRED)));
        insertDismissed(ORG, "iris", SourceType.SLOT, 1103L, "k8s");

        // when
        var result = repo.findPractitioners(ORG, COMMUNITY, Set.of("kubernetes", "k8s"));

        // then
        assertThat(result).extracting(ExpertiseQueryRepository.UserSignals::userId).doesNotContain("iris");
    }

    @Test
    void findPractitioners_readsADismissalThroughTheSignalsOwnCommunity() {

        // given - only another community merges "k8s" into "kubernetes"
        seedUser("ines", true);
        aliases.addAlias(ORG, COMMUNITY + 1, "k8s", "kubernetes");
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 1102L),
                List.of(new SignalWriter.SignalRow("ines", "k8s", SignalType.PRESENTED, OCCURRED)));
        insertDismissed(ORG, "ines", SourceType.SLOT, 1102L, "kubernetes");

        // when
        var result = repo.findPractitioners(ORG, COMMUNITY, Set.of("k8s"));

        // then
        assertThat(result).extracting(ExpertiseQueryRepository.UserSignals::userId).contains("ines");
    }

    @Test
    void findPractitionersExcludesADeprovisionedMemberAndAnErasedSurrogate() {

        // given - "still-here" is still employed, "deprovisioned" was deprovisioned via SCIM (identity_user row
        // present, active = false), and "erased:0000" is a signal left behind by a subject whose identity_user row
        // no longer exists at all
        seedUser("still-here", true);
        seedUser("deprovisioned", false);
        seedSignal("still-here", "kafka");
        seedSignal("deprovisioned", "kafka");
        seedSignal("erased:0000", "kafka");

        // when
        var result = repo.findPractitioners(ORG, COMMUNITY, Set.of("kafka"));

        // then - only the present member is suggested as a practitioner
        assertThat(result).extracting(ExpertiseQueryRepository.UserSignals::userId).containsExactly("still-here");
    }

    @Test
    void findPractitioners_excludesDismissedSignal() {

        // given — eve has a kafka SLOT signal she dismissed, plus a non-dismissed artifact signal
        seedUser("eve", true);
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 1005L),
                List.of(new SignalWriter.SignalRow("eve", "kafka", SignalType.PRESENTED, OCCURRED)));
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 2005L),
                List.of(new SignalWriter.SignalRow("eve", "kafka", SignalType.CONTRIBUTED, OCCURRED)));
        insertDismissed(ORG, "eve", SourceType.SLOT, 1005L, "kafka");

        // when
        var result = repo.findPractitioners(ORG, COMMUNITY, Set.of("kafka"));

        // then — only the artifact signal survives; the dismissed slot signal is gone
        assertThat(result).hasSize(1);
        assertThat(result.get(0).signals()).hasSize(1);
        assertThat(result.get(0).signals().get(0).sourceType()).isEqualTo(SourceType.ARTIFACT);
    }

    // -----------------------------------------------------------------------
    // findPractitioners — tenant + community scoping
    // -----------------------------------------------------------------------

    @Test
    void findPractitioners_doesNotReturnRowsFromAnotherOrg() {

        // given - frank is a present, active member of "other-org" with a kafka signal there
        seedUser("other-org", "frank", true);
        writer.replaceSource(new SignalSource("other-org", COMMUNITY, SourceType.SLOT, 1006L),
                List.of(new SignalWriter.SignalRow("frank", "kafka", SignalType.PRESENTED, OCCURRED)));

        // when - query scoped to "default"
        var result = repo.findPractitioners(ORG, COMMUNITY, Set.of("kafka"));

        // then - frank is not present because the org filter excludes him, not the identity_user join
        assertThat(result).extracting(ExpertiseQueryRepository.UserSignals::userId).doesNotContain("frank");
    }

    @Test
    void findPractitioners_doesNotReturnRowsFromAnotherCommunity() {

        // given - grace is a present member with a kafka signal in community 99
        seedUser("grace", true);
        writer.replaceSource(new SignalSource(ORG, 99L, SourceType.SLOT, 1007L),
                List.of(new SignalWriter.SignalRow("grace", "kafka", SignalType.PRESENTED, OCCURRED)));

        // when - query scoped to community 1
        var result = repo.findPractitioners(ORG, COMMUNITY, Set.of("kafka"));

        // then - grace is not present because the community filter excludes her, not the identity_user join
        assertThat(result).extracting(ExpertiseQueryRepository.UserSignals::userId).doesNotContain("grace");
    }

    // -----------------------------------------------------------------------
    // findForUser — self-view bypasses preferences
    // -----------------------------------------------------------------------

    @Test
    void findForUser_returnsSignalsEvenWhenOptedOut() {

        // given — henry has opted out but still has signals
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 1008L),
                List.of(new SignalWriter.SignalRow("henry", "kafka", SignalType.PRESENTED, OCCURRED)));
        insertOptOut(ORG, "henry");

        // when
        var result = repo.findForUser(ORG, "henry");

        // then — henry sees his own signal despite opt-out
        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo("henry");
        assertThat(result.get(0).signals()).hasSize(1);
    }

    @Test
    void findForUser_returnsSignalsEvenWhenTagHidden() {

        // given — iris has hidden kafka but has a kafka signal
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 1009L),
                List.of(new SignalWriter.SignalRow("iris", "kafka", SignalType.PRESENTED, OCCURRED)));
        insertHiddenTag(ORG, "iris", "kafka");

        // when
        var result = repo.findForUser(ORG, "iris");

        // then — iris sees the hidden signal in her own view
        assertThat(result).hasSize(1);
        assertThat(result.get(0).signals()).hasSize(1);
        assertThat(result.get(0).signals().get(0).tag()).isEqualTo("kafka");
    }

    @Test
    void findForUser_returnsEmptyWhenNoSignals() {

        // when — user with no signals at all
        var result = repo.findForUser(ORG, "nobody");

        // then
        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // allTags
    // -----------------------------------------------------------------------

    @Test
    void allTags_returnsDistinctTagsForCommunity() {

        // given — multiple signals for same tag by different users
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 1010L),
                List.of(new SignalWriter.SignalRow("jack", "kafka", SignalType.PRESENTED, OCCURRED),
                        new SignalWriter.SignalRow("jack", "java", SignalType.PRESENTED, OCCURRED)));
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 2010L),
                List.of(new SignalWriter.SignalRow("kate", "kafka", SignalType.CONTRIBUTED, OCCURRED)));

        // when
        var tags = repo.allTags(ORG, COMMUNITY);

        // then — kafka appears once despite multiple signals
        assertThat(tags).contains("kafka", "java");
        assertThat(tags.stream().filter("kafka"::equals).count()).isEqualTo(1);
    }

    @Test
    void allTags_doesNotReturnTagsFromAnotherCommunity() {

        // given — lena has a tag in community 88
        writer.replaceSource(new SignalSource(ORG, 88L, SourceType.SLOT, 1011L),
                List.of(new SignalWriter.SignalRow("lena", "rust", SignalType.PRESENTED, OCCURRED)));

        // when — query community 1 (assuming this test runs isolated with unique tags)
        var tags = repo.allTags(ORG, COMMUNITY);

        // then — "rust" is only in community 88, not 1
        assertThat(tags).doesNotContain("rust");
    }

    @Test
    void userTagAggregatesAndAllTagsIgnoreADeclaration() {

        // given - dana declared kafka and has no signal at all
        seedUser("dana", true);
        jdbc.update(
                "INSERT INTO expertise_declaration (organization_id, community_id, user_id, tag, declared_at) "
                        + "VALUES (:org, :community, 'dana', 'kafka', '2026-09-12T12:00:00Z')",
                new MapSqlParameterSource("org", ORG).addValue("community", COMMUNITY));

        // when / then
        assertThat(repo.userTagAggregates(ORG, COMMUNITY)).isEmpty();
        assertThat(repo.allTags(ORG, COMMUNITY)).isEmpty();
        assertThat(repo.findPractitioners(ORG, COMMUNITY, Set.of("kafka"))).isEmpty();
    }

    // -----------------------------------------------------------------------
    // userTagAggregates
    // -----------------------------------------------------------------------

    @Test
    void userTagAggregates_returnsOneRowPerUserAndTagWithCountsPerKind() {

        // given - ivy presented kafka twice and contributed one kafka artifact
        seedUser("ivy", true);
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 5001L),
                List.of(new SignalWriter.SignalRow("ivy", "kafka", SignalType.PRESENTED, OCCURRED)));
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 5002L),
                List.of(new SignalWriter.SignalRow("ivy", "kafka", SignalType.PRESENTED, OCCURRED)));
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 5003L),
                List.of(new SignalWriter.SignalRow("ivy", "kafka", SignalType.CONTRIBUTED, OCCURRED)));

        // when
        var result = repo.userTagAggregates(ORG, COMMUNITY);

        // then
        assertThat(result).filteredOn(r -> r.userId().equals("ivy")).singleElement().satisfies(r -> {
            assertThat(r.tag()).isEqualTo("kafka");
            assertThat(r.presented()).isEqualTo(2);
            assertThat(r.contributed()).isEqualTo(1);
            assertThat(r.offered()).isZero();
            assertThat(r.lastOccurredAt()).isEqualTo(OCCURRED);
        });
    }

    @Test
    void userTagAggregates_excludesAnOptedOutUser() {

        // given
        seedUser("jonah", true);
        seedSignal("jonah", "flink");
        insertOptOut(ORG, "jonah");

        // when
        var result = repo.userTagAggregates(ORG, COMMUNITY);

        // then - absent because of the opt-out, not because he is unseeded
        assertThat(result).extracting(ExpertiseQueryRepository.UserTagAggregate::userId).doesNotContain("jonah");
    }

    @Test
    void userTagAggregates_excludesOnlyTheHiddenTag() {

        // given - kim has two tags and has hidden one of them
        seedUser("kim", true);
        seedSignal("kim", "kafka");
        seedSignal("kim", "java");
        insertHiddenTag(ORG, "kim", "kafka");

        // when
        var result = repo.userTagAggregates(ORG, COMMUNITY);

        // then
        assertThat(result).filteredOn(r -> r.userId().equals("kim"))
                .extracting(ExpertiseQueryRepository.UserTagAggregate::tag).containsExactly("java");
    }

    @Test
    void userTagAggregates_ignoresAHiddenSpellingTheSignalsCommunityReadsAsAnotherArea() {

        // given
        seedUser("kent", true);
        aliases.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        seedSignal("kent", "k8s");
        insertHiddenTag(ORG, "kent", "k8s");

        // when
        var result = repo.userTagAggregates(ORG, COMMUNITY);

        // then
        assertThat(result).filteredOn(r -> r.userId().equals("kent"))
                .extracting(ExpertiseQueryRepository.UserTagAggregate::tag).containsExactly("k8s");
    }

    @Test
    void userTagAggregates_excludesASignalUnderAVariantWhoseAreaIsHidden() {

        // given
        seedUser("kira", true);
        aliases.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        seedSignal("kira", "k8s");
        seedSignal("kira", "java");
        insertHiddenTag(ORG, "kira", "kubernetes");

        // when
        var result = repo.userTagAggregates(ORG, COMMUNITY);

        // then
        assertThat(result).filteredOn(r -> r.userId().equals("kira"))
                .extracting(ExpertiseQueryRepository.UserTagAggregate::tag).containsExactly("java");
    }

    @Test
    void userTagAggregates_excludesASignalUnderAVariantDismissedUnderItsArea() {

        // given
        seedUser("lena", true);
        aliases.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 5030L),
                List.of(new SignalWriter.SignalRow("lena", "k8s", SignalType.PRESENTED, OCCURRED)));
        insertDismissed(ORG, "lena", SourceType.SLOT, 5030L, "kubernetes");

        // when
        var result = repo.userTagAggregates(ORG, COMMUNITY);

        // then
        assertThat(result).filteredOn(r -> r.userId().equals("lena")).isEmpty();
    }

    @Test
    void userTagAggregates_excludesADismissedSignal() {

        // given - liam's only kafka signal is one he dismissed
        seedUser("liam", true);
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 5010L),
                List.of(new SignalWriter.SignalRow("liam", "kafka", SignalType.PRESENTED, OCCURRED)));
        insertDismissed(ORG, "liam", SourceType.SLOT, 5010L, "kafka");

        // when
        var result = repo.userTagAggregates(ORG, COMMUNITY);

        // then
        assertThat(result).filteredOn(r -> r.userId().equals("liam")).isEmpty();
    }

    @Test
    void userTagAggregates_excludesADeprovisionedMember() {

        // given - mona left the organization, so her identity_user row is inactive
        seedUser("mona", false);
        seedSignal("mona", "istio");

        // when
        var result = repo.userTagAggregates(ORG, COMMUNITY);

        // then
        assertThat(result).extracting(ExpertiseQueryRepository.UserTagAggregate::userId).doesNotContain("mona");
    }

    @Test
    void userTagAggregates_doesNotReturnRowsFromAnotherCommunity() {

        // given
        seedUser("nils", true);
        writer.replaceSource(new SignalSource(ORG, COMMUNITY + 1, SourceType.SLOT, 5020L),
                List.of(new SignalWriter.SignalRow("nils", "argocd", SignalType.PRESENTED, OCCURRED)));

        // when
        var result = repo.userTagAggregates(ORG, COMMUNITY);

        // then
        assertThat(result).extracting(ExpertiseQueryRepository.UserTagAggregate::userId).doesNotContain("nils");
    }

    // -----------------------------------------------------------------------
    // visibleSignals
    // -----------------------------------------------------------------------

    @Test
    void visibleSignals_returnsNewestFirst() {

        seedUser("alice", true);
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 3001L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.PRESENTED, OCCURRED)));
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 3002L), List
                .of(new SignalWriter.SignalRow("alice", "kafka", SignalType.CONTRIBUTED, OCCURRED.plusSeconds(3600))));

        var result = repo.visibleSignals(ORG, COMMUNITY, 10);

        assertThat(result).extracting(ExpertiseSignal::sourceId).containsExactly(3002L, 3001L);
    }

    @Test
    void visibleSignals_honoursLimit() {

        seedUser("alice", true);
        for (long i = 0; i < 5; i++) {
            writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 3100L + i), List
                    .of(new SignalWriter.SignalRow("alice", "kafka", SignalType.CONTRIBUTED, OCCURRED.plusSeconds(i))));
        }

        assertThat(repo.visibleSignals(ORG, COMMUNITY, 2)).hasSize(2);
    }

    @Test
    void visibleSignals_excludesOptedOutUser() {

        seedUser("alice", true);
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 3201L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.PRESENTED, OCCURRED)));
        insertOptOut(ORG, "alice");

        assertThat(repo.visibleSignals(ORG, COMMUNITY, 10)).isEmpty();
    }

    @Test
    void visibleSignals_excludesHiddenArea() {

        seedUser("alice", true);
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 3301L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.PRESENTED, OCCURRED)));
        insertHiddenTag(ORG, "alice", "kafka");

        assertThat(repo.visibleSignals(ORG, COMMUNITY, 10)).isEmpty();
    }

    @Test
    void visibleSignals_excludesDismissedSignal() {

        seedUser("alice", true);
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 3401L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.PRESENTED, OCCURRED)));
        insertDismissed(ORG, "alice", SourceType.SLOT, 3401L, "kafka");

        assertThat(repo.visibleSignals(ORG, COMMUNITY, 10)).isEmpty();
    }

    @Test
    void visibleSignals_excludesInactiveUser() {

        seedUser("bob", false);
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 3501L),
                List.of(new SignalWriter.SignalRow("bob", "kafka", SignalType.PRESENTED, OCCURRED)));

        assertThat(repo.visibleSignals(ORG, COMMUNITY, 10)).isEmpty();
    }

    @Test
    void visibleSignals_zeroLimit_returnsEmpty() {

        assertThat(repo.visibleSignals(ORG, COMMUNITY, 0)).isEmpty();
    }

    // -----------------------------------------------------------------------
    // findVisibleForUser
    // -----------------------------------------------------------------------

    @Test
    void findVisibleForUser_returnsTheUsersSignalsAcrossCommunities() {

        seedUser("alice", true);
        seedUser("bob", true);
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 5001L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.PRESENTED, OCCURRED)));
        writer.replaceSource(new SignalSource(ORG, 2L, SourceType.ARTIFACT, 5002L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.CONTRIBUTED, OCCURRED)));
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 5003L),
                List.of(new SignalWriter.SignalRow("bob", "kafka", SignalType.PRESENTED, OCCURRED)));

        var result = repo.findVisibleForUser(ORG, "alice");

        assertThat(result).extracting(ExpertiseSignal::sourceId).containsExactlyInAnyOrder(5001L, 5002L);
    }

    @Test
    void findVisibleForUser_excludesAnOptOut() {

        seedUser("alice", true);
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 5101L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.PRESENTED, OCCURRED)));
        insertOptOut(ORG, "alice");

        assertThat(repo.findVisibleForUser(ORG, "alice")).isEmpty();
    }

    @Test
    void findVisibleForUser_excludesAHiddenAreaReadThroughTheSignalsOwnCommunity() {

        seedUser("alice", true);
        aliases.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 5201L),
                List.of(new SignalWriter.SignalRow("alice", "k8s", SignalType.PRESENTED, OCCURRED)));
        writer.replaceSource(new SignalSource(ORG, 2L, SourceType.SLOT, 5202L),
                List.of(new SignalWriter.SignalRow("alice", "k8s", SignalType.PRESENTED, OCCURRED)));
        insertHiddenTag(ORG, "alice", "kubernetes");

        assertThat(repo.findVisibleForUser(ORG, "alice")).extracting(ExpertiseSignal::sourceId).containsExactly(5202L);
    }

    @Test
    void findVisibleForUser_excludesADismissedSignal() {

        seedUser("alice", true);
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 5301L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.PRESENTED, OCCURRED)));
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 5302L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.CONTRIBUTED, OCCURRED)));
        insertDismissed(ORG, "alice", SourceType.SLOT, 5301L, "kafka");

        assertThat(repo.findVisibleForUser(ORG, "alice")).extracting(ExpertiseSignal::sourceId).containsExactly(5302L);
    }

    @Test
    void findVisibleForUser_excludesAnInactiveMember() {

        seedUser("alice", false);
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 5401L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.PRESENTED, OCCURRED)));

        assertThat(repo.findVisibleForUser(ORG, "alice")).isEmpty();
    }

    @Test
    void findVisibleForUser_doesNotReturnAnotherOrganizationsRows() {

        seedUser("other-org", "alice", true);
        writer.replaceSource(new SignalSource("other-org", COMMUNITY, SourceType.SLOT, 5501L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.PRESENTED, OCCURRED)));

        assertThat(repo.findVisibleForUser(ORG, "alice")).isEmpty();
    }

    // -----------------------------------------------------------------------
    // findForUserIn / findVisibleForUserIn - the self view inside one community
    // -----------------------------------------------------------------------

    @Test
    void findForUserIn_returnsEveryRecordedSignalOfTheUserInThatCommunityOnly() {

        // given - hidden, dismissed and opted out all still count for the owner's own view
        seedUser("alice", true);
        seedUser("bob", true);
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 5601L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.PRESENTED, OCCURRED)));
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 5602L),
                List.of(new SignalWriter.SignalRow("alice", "java", SignalType.CONTRIBUTED, OCCURRED)));
        writer.replaceSource(new SignalSource(ORG, 2L, SourceType.SLOT, 5603L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.PRESENTED, OCCURRED)));
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 5604L),
                List.of(new SignalWriter.SignalRow("bob", "kafka", SignalType.PRESENTED, OCCURRED)));
        insertOptOut(ORG, "alice");
        insertHiddenTag(ORG, "alice", "kafka");
        insertDismissed(ORG, "alice", SourceType.ARTIFACT, 5602L, "java");

        // when
        var actual = repo.findForUserIn(ORG, COMMUNITY, "alice");

        // then
        assertThat(actual).extracting(ExpertiseSignal::sourceId).containsExactly(5601L, 5602L);
    }

    @Test
    void findForUserIn_doesNotReturnAnotherOrganizationsRows() {

        // given
        writer.replaceSource(new SignalSource("other-org", COMMUNITY, SourceType.SLOT, 5611L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.PRESENTED, OCCURRED)));

        // when
        var actual = repo.findForUserIn(ORG, COMMUNITY, "alice");

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void findVisibleForUserIn_returnsOnlyWhatThatCommunitySees() {

        // given - a dismissed signal, an area hidden through this community's alias, another community, another user
        seedUser("alice", true);
        seedUser("bob", true);
        aliases.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 5701L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.PRESENTED, OCCURRED)));
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 5702L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.CONTRIBUTED, OCCURRED)));
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 5703L),
                List.of(new SignalWriter.SignalRow("alice", "k8s", SignalType.PRESENTED, OCCURRED)));
        writer.replaceSource(new SignalSource(ORG, 2L, SourceType.SLOT, 5704L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.PRESENTED, OCCURRED)));
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 5705L),
                List.of(new SignalWriter.SignalRow("bob", "kafka", SignalType.PRESENTED, OCCURRED)));
        insertDismissed(ORG, "alice", SourceType.ARTIFACT, 5702L, "kafka");
        insertHiddenTag(ORG, "alice", "kubernetes");

        // when
        var actual = repo.findVisibleForUserIn(ORG, COMMUNITY, "alice");

        // then
        assertThat(actual).extracting(ExpertiseSignal::sourceId).containsExactly(5701L);
    }

    @Test
    void findVisibleForUserIn_excludesAnOptOut() {

        // given
        seedUser("alice", true);
        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 5711L),
                List.of(new SignalWriter.SignalRow("alice", "kafka", SignalType.PRESENTED, OCCURRED)));
        insertOptOut(ORG, "alice");

        // when
        var actual = repo.findVisibleForUserIn(ORG, COMMUNITY, "alice");

        // then
        assertThat(actual).isEmpty();
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private long nextSourceId = 4001L;

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

    private void seedSignal(String userId, String tag) {

        writer.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, nextSourceId++),
                List.of(new SignalWriter.SignalRow(userId, tag, SignalType.PRESENTED, OCCURRED)));
    }

    private void insertOptOut(String org, String userId) {

        jdbc.update(
                "INSERT INTO expertise_opt_out (organization_id, user_id) VALUES (:org, :userId) ON CONFLICT DO NOTHING",
                new MapSqlParameterSource("org", org).addValue("userId", userId));
    }

    private void insertHiddenTag(String org, String userId, String tag) {

        jdbc.update(
                "INSERT INTO expertise_hidden_tag (organization_id, user_id, tag)"
                        + " VALUES (:org, :userId, :tag) ON CONFLICT DO NOTHING",
                new MapSqlParameterSource("org", org).addValue("userId", userId).addValue("tag", tag));
    }

    private void insertDismissed(String org, String userId, SourceType sourceType, Long sourceId, String tag) {

        jdbc.update(
                "INSERT INTO expertise_dismissed_signal (organization_id, user_id, source_type, source_id, tag)"
                        + " VALUES (:org, :userId, :sourceType, :sourceId, :tag) ON CONFLICT DO NOTHING",
                new MapSqlParameterSource("org", org).addValue("userId", userId)
                        .addValue("sourceType", sourceType.name()).addValue("sourceId", sourceId).addValue("tag", tag));
    }
}
