package dev.axonia.expertise.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.expertise.application.port.out.DeclarationRepository;
import dev.axonia.expertise.application.port.out.PreferenceRepository;
import dev.axonia.expertise.application.port.out.TagAliasRepository;
import dev.axonia.expertise.domain.Declaration;
import dev.axonia.expertise.domain.SignalRef;
import dev.axonia.expertise.domain.SignalRow;
import dev.axonia.expertise.domain.SignalSource;
import dev.axonia.expertise.domain.SignalType;
import dev.axonia.expertise.domain.SourceType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link SignalWriter}.
 *
 * <p>
 * Runs as the Testcontainers superuser (table owner), which bypasses RLS — no {@code app.current_org} session variable
 * is needed. This matches the pattern used across all {@code JdbcTestSupport} integration tests in this codebase.
 *
 * <p>
 * {@link SignalWriter} is a {@code @Component}, not a Spring Data repository, so the {@code @DataJdbcTest} slice does
 * not register it automatically — it is imported via {@link ExpertiseJdbcTestSupport}.
 */
public class SignalWriterTest extends ExpertiseJdbcTestSupport {

    @Autowired
    private SignalWriter signalWriter;

    @Autowired
    private PreferenceRepository preferences;

    @Autowired
    private DeclarationRepository declarations;

    @Autowired
    private TagAliasRepository aliases;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private static final String ORG = "default";
    private static final Long COMMUNITY = 1L;
    private static final Long OTHER_COMMUNITY = 2L;
    private static final String ALICE = "alice";
    private static final Instant OCCURRED = Instant.parse("2026-01-15T10:00:00Z");

    // -----------------------------------------------------------------------
    // replaceSource
    // -----------------------------------------------------------------------

    @Test
    void replaceSource_insertsNormalisedRows() {

        // given
        var rows = List.of(new SignalRow("alice", "Kafka", SignalType.PRESENTED, OCCURRED),
                new SignalRow("alice", "Spring Boot", SignalType.PRESENTED, OCCURRED));

        // when
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 100L), rows);

        // then
        var signals = signalsForSource(SourceType.SLOT, 100L);
        assertThat(signals).hasSize(2);
        assertThat(signals).extracting(r -> r.get("tag")).containsExactlyInAnyOrder("kafka", "spring-boot");
        assertThat(signals).extracting(r -> r.get("user_id")).containsOnly("alice");
        assertThat(signals).extracting(r -> r.get("signal_type")).containsOnly("PRESENTED");
    }

    @Test
    void replaceSource_secondCallReplacesFirst() {

        // given — first batch
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 200L),
                List.of(new SignalRow("bob", "java", SignalType.PRESENTED, OCCURRED)));

        // when — second batch with different tags
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 200L),
                List.of(new SignalRow("bob", "kotlin", SignalType.PRESENTED, OCCURRED)));

        // then — only the second batch survives
        var signals = signalsForSource(SourceType.SLOT, 200L);
        assertThat(signals).hasSize(1);
        assertThat(signals.get(0).get("tag")).isEqualTo("kotlin");
    }

    @Test
    void replaceSource_deduplicatesRows() {

        // given — two rows with the same (userId, normalizedTag, signalType)
        var rows = List.of(new SignalRow("carol", "Kafka", SignalType.PRESENTED, OCCURRED),
                new SignalRow("carol", "kafka", SignalType.PRESENTED, OCCURRED.plusSeconds(60)));

        // when
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 300L), rows);

        // then — only one row (first-seen wins)
        assertThat(signalsForSource(SourceType.SLOT, 300L)).hasSize(1);
    }

    @Test
    void replaceSource_dropsBlankTagsAfterNormalisation() {

        // given — tag that normalises to blank (e.g. only special chars)
        var rows = List.of(new SignalRow("dana", "---", SignalType.CONTRIBUTED, OCCURRED),
                new SignalRow("dana", "java", SignalType.CONTRIBUTED, OCCURRED));

        // when
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 400L), rows);

        // then — only the "java" row is inserted; "---" normalises to blank
        var signals = signalsForSource(SourceType.ARTIFACT, 400L);
        assertThat(signals).hasSize(1);
        assertThat(signals.get(0).get("tag")).isEqualTo("java");
    }

    @Test
    void replaceSource_withEmptyRows_deletesExisting() {

        // given — seed some existing signals
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.TOPIC, 500L),
                List.of(new SignalRow("eve", "python", SignalType.OFFERED, OCCURRED)));
        assertThat(signalsForSource(SourceType.TOPIC, 500L)).hasSize(1);

        // when — replace with empty list
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.TOPIC, 500L), List.of());

        // then — all signals for this source are gone
        assertThat(signalsForSource(SourceType.TOPIC, 500L)).isEmpty();
    }

    // -----------------------------------------------------------------------
    // deleteSource
    // -----------------------------------------------------------------------

    @Test
    void deleteSource_removesAllSignalsForThatSource() {

        // given
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 600L),
                List.of(new SignalRow("frank", "rust", SignalType.CONTRIBUTED, OCCURRED),
                        new SignalRow("frank", "c", SignalType.CONTRIBUTED, OCCURRED)));
        assertThat(signalsForSource(SourceType.ARTIFACT, 600L)).hasSize(2);

        // when
        signalWriter.deleteSource(ORG, SourceType.ARTIFACT, 600L);

        // then
        assertThat(signalsForSource(SourceType.ARTIFACT, 600L)).isEmpty();
    }

    @Test
    void deleteSource_doesNotAffectOtherSources() {

        // given — two sources
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 700L),
                List.of(new SignalRow("grace", "go", SignalType.PRESENTED, OCCURRED)));
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 701L),
                List.of(new SignalRow("grace", "rust", SignalType.PRESENTED, OCCURRED)));

        // when — delete only 700
        signalWriter.deleteSource(ORG, SourceType.SLOT, 700L);

        // then — 701 is untouched
        assertThat(signalsForSource(SourceType.SLOT, 700L)).isEmpty();
        assertThat(signalsForSource(SourceType.SLOT, 701L)).hasSize(1);
    }

    @Test
    void deleteSource_isIdempotent() {

        // given — source that does not exist
        // when / then — no exception
        signalWriter.deleteSource(ORG, SourceType.TOPIC, 9999L);
    }

    // -----------------------------------------------------------------------
    // Cross-source independence
    // -----------------------------------------------------------------------

    @Test
    void replaceSource_onlyDeletesMatchingSourceTypeAndId() {

        // given — SLOT 800 and ARTIFACT 800 (same id, different types)
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 800L),
                List.of(new SignalRow("henry", "scala", SignalType.PRESENTED, OCCURRED)));
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 800L),
                List.of(new SignalRow("henry", "akka", SignalType.CONTRIBUTED, OCCURRED)));

        // when — replace only SLOT 800
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.SLOT, 800L),
                List.of(new SignalRow("henry", "clojure", SignalType.PRESENTED, OCCURRED)));

        // then — ARTIFACT 800 is untouched
        assertThat(signalsForSource(SourceType.SLOT, 800L)).extracting(r -> r.get("tag")).containsExactly("clojure");
        assertThat(signalsForSource(SourceType.ARTIFACT, 800L)).extracting(r -> r.get("tag")).containsExactly("akka");
    }

    // -----------------------------------------------------------------------
    // renameTag / removeTag - the curator rewrites the projection directly
    // -----------------------------------------------------------------------

    @Test
    void renameTag_rewritesTheCommunitysSignals() {

        // given
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 900L),
                List.of(new SignalRow(ALICE, "kafak", SignalType.CONTRIBUTED, OCCURRED)));

        // when
        signalWriter.renameTag(ORG, COMMUNITY, "kafak", "kafka");

        // then
        assertThat(tagsOf(COMMUNITY)).containsExactly("kafka");
    }

    @Test
    void renameTag_foldsASignalThatAlreadyCarriesTheTarget() {

        // given - the same person signalled both names on the same source
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 901L),
                List.of(new SignalRow(ALICE, "kafak", SignalType.CONTRIBUTED, OCCURRED),
                        new SignalRow(ALICE, "kafka", SignalType.CONTRIBUTED, OCCURRED)));

        // when
        signalWriter.renameTag(ORG, COMMUNITY, "kafak", "kafka");

        // then - uq_expertise_signal survives: one row, not two
        assertThat(tagsOf(COMMUNITY)).containsExactly("kafka");
    }

    @Test
    void renameTag_leavesAnotherCommunitysSignalsAlone() {

        // given
        signalWriter.replaceSource(new SignalSource(ORG, OTHER_COMMUNITY, SourceType.ARTIFACT, 902L),
                List.of(new SignalRow(ALICE, "kafak", SignalType.CONTRIBUTED, OCCURRED)));

        // when
        signalWriter.renameTag(ORG, COMMUNITY, "kafak", "kafka");

        // then
        assertThat(tagsOf(OTHER_COMMUNITY)).containsExactly("kafak");
    }

    @Test
    void removeTag_dropsItsSignalsAndLeavesTheOthers() {

        // given
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 903L),
                List.of(new SignalRow(ALICE, "misc", SignalType.CONTRIBUTED, OCCURRED),
                        new SignalRow(ALICE, "kafka", SignalType.CONTRIBUTED, OCCURRED)));

        // when
        signalWriter.removeTag(ORG, COMMUNITY, "misc");

        // then
        assertThat(tagsOf(COMMUNITY)).containsExactly("kafka");
    }

    @Test
    void removeTag_leavesAnotherCommunitysSignalsAlone() {

        // given
        signalWriter.replaceSource(new SignalSource(ORG, OTHER_COMMUNITY, SourceType.ARTIFACT, 904L),
                List.of(new SignalRow(ALICE, "misc", SignalType.CONTRIBUTED, OCCURRED)));

        // when
        signalWriter.removeTag(ORG, COMMUNITY, "misc");

        // then
        assertThat(tagsOf(OTHER_COMMUNITY)).containsExactly("misc");
    }

    // -----------------------------------------------------------------------
    // carryHiddenArea / dropHiddenArea / carryOverSplit - a hidden area follows the rows that read as it
    // -----------------------------------------------------------------------

    @Test
    void carryHiddenArea_hidesTheNewNameForAMemberWhoHidTheArea() {

        // given
        signal(ALICE, COMMUNITY, "kubernetes", 910L);
        preferences.hideTag(ORG, ALICE, "kubernetes");

        // when
        signalWriter.carryHiddenArea(ORG, COMMUNITY, "kubernetes", "k8s");

        // then
        assertThat(preferences.hiddenTags(ORG, ALICE)).containsExactly("k8s");
    }

    @Test
    void carryHiddenArea_keepsTheOldRowWhileAnotherCommunityReadsARowAsIt() {

        // given
        signal(ALICE, COMMUNITY, "kubernetes", 911L);
        signal(ALICE, OTHER_COMMUNITY, "kubernetes", 912L);
        preferences.hideTag(ORG, ALICE, "kubernetes");

        // when
        signalWriter.carryHiddenArea(ORG, COMMUNITY, "kubernetes", "k8s");

        // then
        assertThat(preferences.hiddenTags(ORG, ALICE)).containsExactlyInAnyOrder("kubernetes", "k8s");
    }

    @Test
    void carryHiddenArea_hidesTheNewNameForAMemberWhoOnlyDeclaredTheAreaHere() {

        // given
        declare("bob", COMMUNITY, "kubernetes");
        preferences.hideTag(ORG, "bob", "kubernetes");

        // when
        signalWriter.carryHiddenArea(ORG, COMMUNITY, "kubernetes", "k8s");

        // then
        assertThat(preferences.hiddenTags(ORG, "bob")).containsExactly("k8s");
    }

    @Test
    void carryHiddenArea_hidesTheNewNameForAMemberWhoDeclaredAVariantOfTheAreaHere() {

        // given
        aliases.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        declare("bob", COMMUNITY, "k8s");
        preferences.hideTag(ORG, "bob", "kubernetes");

        // when
        signalWriter.carryHiddenArea(ORG, COMMUNITY, "kubernetes", "containers");

        // then
        assertThat(preferences.hiddenTags(ORG, "bob")).containsExactly("containers");
    }

    @Test
    void carryHiddenArea_carriesNothingForAMemberWhoseRowsAreInAnotherCommunity() {

        // given
        declare("bob", OTHER_COMMUNITY, "kubernetes");
        preferences.hideTag(ORG, "bob", "kubernetes");

        // when
        signalWriter.carryHiddenArea(ORG, COMMUNITY, "kubernetes", "k8s");

        // then
        assertThat(preferences.hiddenTags(ORG, "bob")).containsExactly("kubernetes");
    }

    @Test
    void carryHiddenArea_carriesNothingForAMemberWithNoRowsAtAll() {

        // given
        preferences.hideTag(ORG, "bob", "kubernetes");

        // when
        signalWriter.carryHiddenArea(ORG, COMMUNITY, "kubernetes", "k8s");

        // then
        assertThat(preferences.hiddenTags(ORG, "bob")).containsExactly("kubernetes");
    }

    @Test
    void carryHiddenArea_ofASpellingThisCommunityReadsAsAnotherAreaCarriesNothingAndDropsTheInertRow() {

        // given - "k8s" reads as "kubernetes" here, so bob's hidden "k8s" hides nothing
        aliases.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        declare("bob", COMMUNITY, "k8s");
        preferences.hideTag(ORG, "bob", "k8s");

        // when
        signalWriter.carryHiddenArea(ORG, COMMUNITY, "k8s", "kube");

        // then
        assertThat(preferences.hiddenTags(ORG, "bob")).isEmpty();
    }

    @Test
    void dropHiddenArea_prunesTheRowOfAMemberWhoOnlyDeclaredTheAreaHere() {

        // given
        declare("bob", COMMUNITY, "misc");
        preferences.hideTag(ORG, "bob", "misc");

        // when
        signalWriter.dropHiddenArea(ORG, COMMUNITY, "misc");

        // then
        assertThat(preferences.hiddenTags(ORG, "bob")).isEmpty();
    }

    @Test
    void dropHiddenArea_keepsTheRowADeclarationInAnotherCommunityStillReadsAs() {

        // given
        signal("bob", COMMUNITY, "misc", 950L);
        declare("bob", OTHER_COMMUNITY, "misc");
        preferences.hideTag(ORG, "bob", "misc");

        // when
        signalWriter.dropHiddenArea(ORG, COMMUNITY, "misc");

        // then
        assertThat(preferences.hiddenTags(ORG, "bob")).containsExactly("misc");
    }

    @Test
    void dropHiddenArea_keepsTheRowAVariantDeclaredInAnotherCommunityStillReadsAs() {

        // given
        aliases.addAlias(ORG, OTHER_COMMUNITY, "k8s", "kubernetes");
        declare("bob", COMMUNITY, "kubernetes");
        declare("bob", OTHER_COMMUNITY, "k8s");
        preferences.hideTag(ORG, "bob", "kubernetes");

        // when
        signalWriter.dropHiddenArea(ORG, COMMUNITY, "kubernetes");

        // then
        assertThat(preferences.hiddenTags(ORG, "bob")).containsExactly("kubernetes");
    }

    @Test
    void dropHiddenArea_keepsTheRowASignalUnderAVariantInAnotherCommunityStillReadsAs() {

        // given
        aliases.addAlias(ORG, OTHER_COMMUNITY, "k8s", "kubernetes");
        signal("bob", COMMUNITY, "kubernetes", 960L);
        signal("bob", OTHER_COMMUNITY, "k8s", 961L);
        preferences.hideTag(ORG, "bob", "kubernetes");

        // when
        signalWriter.dropHiddenArea(ORG, COMMUNITY, "kubernetes");

        // then
        assertThat(preferences.hiddenTags(ORG, "bob")).containsExactly("kubernetes");
    }

    @Test
    void dropHiddenArea_dropsTheRowAnotherCommunityOnlyStoresUnderADifferentArea() {

        // given - the other community reads bob's "k8s" as "containers", so no area there carries "k8s"
        aliases.addAlias(ORG, OTHER_COMMUNITY, "k8s", "containers");
        signal("bob", COMMUNITY, "k8s", 962L);
        signal("bob", OTHER_COMMUNITY, "k8s", 963L);
        preferences.hideTag(ORG, "bob", "k8s");

        // when
        signalWriter.dropHiddenArea(ORG, COMMUNITY, "k8s");

        // then
        assertThat(preferences.hiddenTags(ORG, "bob")).isEmpty();
    }

    @Test
    void dropHiddenArea_leavesAMemberWithNoRowReadingAsTheAreaHereAlone() {

        // given - bob declared "k8s" here, where it is not an alias of "kubernetes"
        aliases.addAlias(ORG, OTHER_COMMUNITY, "k8s", "kubernetes");
        declare("bob", COMMUNITY, "k8s");
        preferences.hideTag(ORG, "bob", "kubernetes");

        // when
        signalWriter.dropHiddenArea(ORG, COMMUNITY, "kubernetes");

        // then
        assertThat(preferences.hiddenTags(ORG, "bob")).containsExactly("kubernetes");
    }

    @Test
    void carryOverSplit_movesTheHiddenAreaToTheVariantForAMemberWhoSignalledIt() {

        // given
        aliases.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        signal("bob", COMMUNITY, "k8s", 970L);
        preferences.hideTag(ORG, "bob", "kubernetes");

        // when
        signalWriter.carryOverSplit(ORG, COMMUNITY, "k8s", "kubernetes");

        // then
        assertThat(preferences.hiddenTags(ORG, "bob")).containsExactly("k8s");
    }

    @Test
    void carryOverSplit_movesTheHiddenAreaToTheVariantForAMemberWhoDeclaredIt() {

        // given
        aliases.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        declare("bob", COMMUNITY, "k8s");
        preferences.hideTag(ORG, "bob", "kubernetes");

        // when
        signalWriter.carryOverSplit(ORG, COMMUNITY, "k8s", "kubernetes");

        // then
        assertThat(preferences.hiddenTags(ORG, "bob")).containsExactly("k8s");
    }

    @Test
    void carryOverSplit_keepsTheAreaWhileARowStoredUnderItStillReadsAsIt() {

        // given
        aliases.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        signal("bob", COMMUNITY, "k8s", 974L);
        signal("bob", COMMUNITY, "kubernetes", 975L);
        preferences.hideTag(ORG, "bob", "kubernetes");

        // when
        signalWriter.carryOverSplit(ORG, COMMUNITY, "k8s", "kubernetes");

        // then
        assertThat(preferences.hiddenTags(ORG, "bob")).containsExactlyInAnyOrder("kubernetes", "k8s");
    }

    @Test
    void carryOverSplit_dismissesTheVariantOnTheSignalsDismissedUnderTheArea() {

        // given - alice dismissed her "k8s" signal on source 971 under the area's name
        signal(ALICE, COMMUNITY, "k8s", 971L);
        preferences.dismissSignal(ORG, ALICE, new SignalRef(SourceType.ARTIFACT, 971L), "kubernetes");

        // when
        signalWriter.carryOverSplit(ORG, COMMUNITY, "k8s", "kubernetes");

        // then
        assertThat(dismissedTags(ALICE, SourceType.ARTIFACT, 971L)).containsExactlyInAnyOrder("kubernetes", "k8s");
    }

    @Test
    void carryOverSplit_leavesADismissalOnASignalStoredUnderTheAreasOwnName() {

        // given
        signal(ALICE, COMMUNITY, "kubernetes", 973L);
        preferences.dismissSignal(ORG, ALICE, new SignalRef(SourceType.ARTIFACT, 973L), "kubernetes");

        // when
        signalWriter.carryOverSplit(ORG, COMMUNITY, "k8s", "kubernetes");

        // then
        assertThat(dismissedTags(ALICE, SourceType.ARTIFACT, 973L)).containsExactly("kubernetes");
    }

    @Test
    void carryOverSplit_leavesMembersWhoseVariantIsInAnotherCommunityAlone() {

        // given - bob's "k8s" signal, declaration and dismissal are all in another community
        aliases.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");
        signal("bob", OTHER_COMMUNITY, "k8s", 972L);
        declare("bob", OTHER_COMMUNITY, "k8s");
        preferences.hideTag(ORG, "bob", "kubernetes");
        preferences.dismissSignal(ORG, "bob", new SignalRef(SourceType.ARTIFACT, 972L), "kubernetes");

        // when
        signalWriter.carryOverSplit(ORG, COMMUNITY, "k8s", "kubernetes");

        // then
        assertThat(preferences.hiddenTags(ORG, "bob")).containsExactly("kubernetes");
        assertThat(dismissedTags("bob", SourceType.ARTIFACT, 972L)).containsExactly("kubernetes");
    }

    @Test
    void renameTag_leavesTheHiddenTagsToCarryHiddenArea() {

        // given
        signal(ALICE, COMMUNITY, "kubernetes", 976L);
        preferences.hideTag(ORG, ALICE, "kubernetes");

        // when
        signalWriter.renameTag(ORG, COMMUNITY, "kubernetes", "k8s");

        // then
        assertThat(preferences.hiddenTags(ORG, ALICE)).containsExactly("kubernetes");
    }

    @Test
    void renameTag_carriesADismissedSignalOntoTheNewName() {

        // given - alice dismissed the signal this artifact produced for her
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 913L),
                List.of(new SignalRow(ALICE, "kafak", SignalType.CONTRIBUTED, OCCURRED)));
        preferences.dismissSignal(ORG, ALICE, new SignalRef(SourceType.ARTIFACT, 913L), "kafak");

        // when
        signalWriter.renameTag(ORG, COMMUNITY, "kafak", "kafka");

        // then - the dismissal follows the signal it was about
        assertThat(dismissedTags(ALICE, SourceType.ARTIFACT, 913L)).containsExactly("kafka");
    }

    @Test
    void renameTag_foldsADismissalThatAlreadyNamesTheTarget() {

        // given - alice dismissed both names on the same source
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 914L),
                List.of(new SignalRow(ALICE, "kafak", SignalType.CONTRIBUTED, OCCURRED),
                        new SignalRow(ALICE, "kafka", SignalType.CONTRIBUTED, OCCURRED)));
        preferences.dismissSignal(ORG, ALICE, new SignalRef(SourceType.ARTIFACT, 914L), "kafak");
        preferences.dismissSignal(ORG, ALICE, new SignalRef(SourceType.ARTIFACT, 914L), "kafka");

        // when
        signalWriter.renameTag(ORG, COMMUNITY, "kafak", "kafka");

        // then - the primary key survives: one dismissal, not two
        assertThat(dismissedTags(ALICE, SourceType.ARTIFACT, 914L)).containsExactly("kafka");
    }

    @Test
    void renameTag_leavesAnotherCommunitysDismissalAlone() {

        // given - the dismissed signal came from a source that belongs to another community
        signalWriter.replaceSource(new SignalSource(ORG, OTHER_COMMUNITY, SourceType.ARTIFACT, 915L),
                List.of(new SignalRow(ALICE, "kafak", SignalType.CONTRIBUTED, OCCURRED)));
        preferences.dismissSignal(ORG, ALICE, new SignalRef(SourceType.ARTIFACT, 915L), "kafak");

        // when
        signalWriter.renameTag(ORG, COMMUNITY, "kafak", "kafka");

        // then
        assertThat(dismissedTags(ALICE, SourceType.ARTIFACT, 915L)).containsExactly("kafak");
    }

    @Test
    void removeTag_dropsThePreferencesItOrphans() {

        // given
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 916L),
                List.of(new SignalRow(ALICE, "misc", SignalType.CONTRIBUTED, OCCURRED)));
        preferences.hideTag(ORG, ALICE, "misc");
        preferences.dismissSignal(ORG, ALICE, new SignalRef(SourceType.ARTIFACT, 916L), "misc");

        // when
        signalWriter.dropHiddenArea(ORG, COMMUNITY, "misc");
        signalWriter.removeTag(ORG, COMMUNITY, "misc");

        // then - nothing is left to pre-hide a later tag that happens to share the name
        assertThat(preferences.hiddenTags(ORG, ALICE)).isEmpty();
        assertThat(dismissedTags(ALICE, SourceType.ARTIFACT, 916L)).isEmpty();
    }

    @Test
    void removeTag_leavesThePreferencesThatStillProtectAnotherCommunity() {

        // given - alice signalled "misc" in both communities
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 917L),
                List.of(new SignalRow(ALICE, "misc", SignalType.CONTRIBUTED, OCCURRED)));
        signalWriter.replaceSource(new SignalSource(ORG, OTHER_COMMUNITY, SourceType.ARTIFACT, 918L),
                List.of(new SignalRow(ALICE, "misc", SignalType.CONTRIBUTED, OCCURRED)));
        preferences.hideTag(ORG, ALICE, "misc");
        preferences.dismissSignal(ORG, ALICE, new SignalRef(SourceType.ARTIFACT, 918L), "misc");

        // when
        signalWriter.dropHiddenArea(ORG, COMMUNITY, "misc");
        signalWriter.removeTag(ORG, COMMUNITY, "misc");

        // then - the other community still calls the tag "misc", and alice is still hidden there
        assertThat(preferences.hiddenTags(ORG, ALICE)).containsExactly("misc");
        assertThat(dismissedTags(ALICE, SourceType.ARTIFACT, 918L)).containsExactly("misc");
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private void signal(String userId, Long communityId, String tag, long sourceId) {

        signalWriter.replaceSource(new SignalSource(ORG, communityId, SourceType.ARTIFACT, sourceId),
                List.of(new SignalRow(userId, tag, SignalType.CONTRIBUTED, OCCURRED)));
    }

    private void declare(String userId, Long communityId, String tag) {

        declarations.insert(ORG,
                Declaration.builder().communityId(communityId).userId(userId).tag(tag).declaredAt(OCCURRED).build());
    }

    private List<String> dismissedTags(String userId, SourceType sourceType, Long sourceId) {

        return jdbc.queryForList(
                "SELECT tag FROM expertise_dismissed_signal" + " WHERE organization_id = :org AND user_id = :userId"
                        + " AND source_type = :sourceType AND source_id = :sourceId",
                new MapSqlParameterSource("org", ORG).addValue("userId", userId)
                        .addValue("sourceType", sourceType.name()).addValue("sourceId", sourceId),
                String.class);
    }

    private List<String> tagsOf(Long communityId) {

        return jdbc.queryForList(
                "SELECT tag FROM expertise_signal"
                        + " WHERE organization_id = :org AND community_id = :communityId AND user_id = :userId",
                new MapSqlParameterSource("org", ORG).addValue("communityId", communityId).addValue("userId", ALICE),
                String.class);
    }

    private List<java.util.Map<String, Object>> signalsForSource(SourceType sourceType, Long sourceId) {

        return jdbc.queryForList(
                "SELECT * FROM expertise_signal"
                        + " WHERE organization_id = :org AND source_type = :sourceType AND source_id = :sourceId",
                new MapSqlParameterSource("org", ORG).addValue("sourceType", sourceType.name()).addValue("sourceId",
                        sourceId));
    }
}
