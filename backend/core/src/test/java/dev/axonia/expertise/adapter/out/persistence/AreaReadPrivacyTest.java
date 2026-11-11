package dev.axonia.expertise.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.expertise.api.OpenPerson;
import dev.axonia.expertise.application.CurationScope;
import dev.axonia.expertise.application.EvidenceEnricher;
import dev.axonia.expertise.application.ExpertiseCurationService;
import dev.axonia.expertise.application.ExpertiseService;
import dev.axonia.expertise.application.port.out.CommunityAccess;
import dev.axonia.expertise.application.port.out.FakeNamedAreaRepository;
import dev.axonia.expertise.application.port.out.PersonDirectory;
import dev.axonia.expertise.domain.Declaration;
import dev.axonia.expertise.domain.ExpertiseArea;
import dev.axonia.expertise.domain.SignalRef;
import dev.axonia.expertise.domain.SignalRow;
import dev.axonia.expertise.domain.SignalSource;
import dev.axonia.expertise.domain.SignalType;
import dev.axonia.expertise.domain.SourceType;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The two reads of a community's areas over a real database: the search's list, which is what a reader may see, and the
 * curator's vocabulary, which is every name the community produced. The claim is that an area's size is the length of
 * the list its card opens, so both sides of it are asserted against the same rows.
 */
class AreaReadPrivacyTest extends ExpertiseJdbcTestSupport {

    private static final String ORG = "default";
    private static final String SLUG = "area-reads";
    private static final String CURATOR = "area-curator";
    private static final Instant AT = Instant.parse("2026-09-01T12:00:00Z");
    private static final AtomicLong SOURCES = new AtomicLong(980_000L);

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
    private ExpertiseService search;
    private ExpertiseCurationService curation;
    private PersonDirectory personDirectory;
    private CurationScope curationScope;

    @BeforeEach
    void wireTheTwoServicesOverOneCommunity() {

        community = seedCommunity();
        seedMemberUser(CURATOR);
        var communityAccess = mock(CommunityAccess.class);
        when(communityAccess.communityIdForSlug(SLUG)).thenReturn(community);
        when(communityAccess.canCurateTags(community, CURATOR)).thenReturn(true);
        personDirectory = mock(PersonDirectory.class);
        when(personDirectory.resolve(any())).thenReturn(Map.of());
        var evidenceEnricher = mock(EvidenceEnricher.class);
        when(evidenceEnricher.enrich(any(), any())).thenAnswer(call -> call.getArgument(0));
        search = new ExpertiseService(() -> ORG, () -> CURATOR, communityAccess, personDirectory, queries, aliases,
                evidenceEnricher, declarations);
        curationScope = new CurationScope(() -> CURATOR, communityAccess, queries, declarations, aliases, List.of());
        curation = new ExpertiseCurationService(() -> ORG, curationScope, queries, aliases, dismissals, List.of(),
                signalWriter, declarations, new FakeNamedAreaRepository(), vocabularyLock);
    }

    @Test
    void anAreasCountIsTheLengthOfTheListItsCardOpens() {

        // given - every privacy choice is represented under "kafka", and "terraform" holds one dismissed signal
        seedMemberUser("area-alice");
        seedMemberUser("area-bob");
        seedMemberUser("area-carol");
        seedMemberUser("area-dana");
        seedMemberUser("area-erin");
        signal("area-alice", "kafka");
        signal("area-bob", "kafka");
        preferences.setOptOut(ORG, "area-bob", true);
        signal("area-carol", "kafka");
        preferences.hideTag(ORG, "area-carol", "kafka");
        declare("area-dana", "kafka");
        declare("area-erin", "kafka");
        preferences.setOptOut(ORG, "area-erin", true);
        dismiss("area-alice", signal("area-alice", "terraform"), "terraform");
        signal("area-carol", "terraform");

        // when
        var areas = search.areas(SLUG, null);

        // then - alice and dana under "kafka", carol alone under "terraform"
        assertThat(areas).extracting(ExpertiseArea::canonicalTag, ExpertiseArea::peopleCount)
                .containsExactly(tuple("kafka", 2), tuple("terraform", 1));
        assertThat(areas).allSatisfy(area -> assertThat(area.peopleCount())
                .isEqualTo(search.whoKnows(SLUG, area.canonicalTag(), false).size()));
    }

    @Test
    void theVocabularyKeepsTheNamesTheSearchDropsWhenEveryCarrierIsHidden() {

        // given - dana also declared "argo", which nobody else carries
        strandHelmBehindAnOptedOutDeclarer();
        declare("area-dana", "argo");

        // then - the search lists what a reader can act on
        assertThat(search.areas(SLUG, null))
                .extracting(ExpertiseArea::canonicalTag, ExpertiseArea::variants, ExpertiseArea::peopleCount)
                .containsExactly(tuple("kubernetes", List.of("kubernetes"), 1));

        // and - the curator sees every name the community produced, the merged chip included, counted as the search
        assertThat(curation.vocabulary(SLUG))
                .extracting(ExpertiseArea::canonicalTag, ExpertiseArea::variants, ExpertiseArea::peopleCount)
                .containsExactly(tuple("argo", List.of("argo"), 0),
                        tuple("kubernetes", List.of("helm", "kubernetes"), 1));
    }

    @Test
    void theChipTheVocabularyDrawsIsTheOneTheRefusalAsksACuratorToSplit() {

        // given
        strandHelmBehindAnOptedOutDeclarer();

        // then - both verbs refuse while the variant stands
        assertThatThrownBy(() -> curation.rename(SLUG, "kubernetes", "orchestration"))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Split them out first");
        assertThatThrownBy(() -> curation.delete(SLUG, "kubernetes")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Split them out first");

        // when - the curator splits the chip the card draws
        curation.split(SLUG, "helm");

        // then
        assertThatCode(() -> curation.rename(SLUG, "kubernetes", "orchestration")).doesNotThrowAnyException();
        assertThatCode(() -> curation.delete(SLUG, "orchestration")).doesNotThrowAnyException();
        assertThat(curation.vocabulary(SLUG)).extracting(ExpertiseArea::canonicalTag).containsExactly("helm");
    }

    @Test
    void openInExcludesPausedOptedOutAndHidden() {

        // given - four declarers of kafka, three of whom a reader must not find
        for (var userId : List.of("area-alice", "area-bob", "area-carol", "area-dana")) {
            seedMemberUser(userId);
            declare(userId, "kafka");
        }
        preferences.setPaused(ORG, "area-bob", true);
        preferences.setOptOut(ORG, "area-carol", true);
        preferences.hideTag(ORG, "area-dana", "kafka");
        when(personDirectory.found(any())).thenAnswer(call -> {
            java.util.Collection<String> ids = call.getArgument(0);
            return ids.stream().collect(java.util.stream.Collectors.toMap(id -> id,
                    id -> new dev.axonia.expertise.domain.Person(id, id, null, null)));
        });

        // when
        var open = search.openIn(SLUG);

        // then
        assertThat(open).singleElement().satisfies(area -> {
            assertThat(area.canonicalTag()).isEqualTo("kafka");
            assertThat(area.people()).extracting(OpenPerson::userId).containsExactly("area-alice");
        });
    }

    @Test
    void aTagOnlyAPausedDeclarerCarriesIsAbsentFromCountsAndVisibleTagsButKeptAsARawName() {

        // given - alice declared kafka and is paused, bob declared kafka and is not; alice alone declared argo
        seedMemberUser("area-alice");
        seedMemberUser("area-bob");
        declare("area-alice", "kafka");
        declare("area-bob", "kafka");
        declare("area-alice", "argo");
        preferences.setPaused(ORG, "area-alice", true);

        // then - the curator's counts agree with the search: alice counts nowhere
        assertThat(curation.vocabulary(SLUG)).extracting(ExpertiseArea::canonicalTag, ExpertiseArea::peopleCount)
                .containsExactly(tuple("argo", 0), tuple("kafka", 1));
        assertThat(search.areas(SLUG, null)).extracting(ExpertiseArea::canonicalTag, ExpertiseArea::peopleCount)
                .containsExactly(tuple("kafka", 1));

        // and - the member-facing tags drop argo, while the raw names a curator works on keep it
        assertThat(curationScope.visibleTagsOf(ORG, community)).containsExactly("kafka");
        assertThat(curationScope.rawTagsOf(ORG, community)).contains("argo", "kafka");
    }

    /** Dana's declaration is the only carrier of "helm", merged into "kubernetes", and she then opted out. */
    private void strandHelmBehindAnOptedOutDeclarer() {

        seedMemberUser("area-dana");
        signal(CURATOR, "kubernetes");
        declare("area-dana", "helm");
        curation.merge(SLUG, "helm", "kubernetes");
        preferences.setOptOut(ORG, "area-dana", true);
    }

    private void dismiss(String userId, SignalSource source, String tag) {

        preferences.dismissSignal(ORG, userId, new SignalRef(source.sourceType(), source.sourceId()), tag);
    }

    private SignalSource signal(String userId, String tag) {

        var source = new SignalSource(ORG, community, SourceType.ARTIFACT, SOURCES.incrementAndGet());
        signalWriter.replaceSource(source, List.of(new SignalRow(userId, tag, SignalType.CONTRIBUTED, AT)));
        return source;
    }

    private void declare(String userId, String tag) {

        declarations.insert(ORG,
                Declaration.builder().communityId(community).userId(userId).tag(tag).declaredAt(AT).build());
    }

    private void seedMemberUser(String userId) {

        jdbc.update("""
                INSERT INTO identity_user (organization_id, user_id, display_name, email, active, scim_id, arrival,
                                           created_at, updated_at, version)
                VALUES (:org, :userId, :userId, :userId || '@example.test', true, 'scim-' || :userId, 'SYNCED',
                        '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', 0)
                """, new MapSqlParameterSource("org", ORG).addValue("userId", userId));
        jdbc.update(
                "INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                        + "version) VALUES (:org, :community, :userId, 'MEMBER', now(), now(), 0)",
                Map.of("org", ORG, "community", community, "userId", userId));
    }

    /** Seeds its own kind by label, never {@code min(id)}: the baseline seeds no reference catalog. */
    private long seedCommunity() {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES (:org, 'Area Guild', now(), now(), 0) ON CONFLICT DO NOTHING", Map.of("org", ORG));
        var kindId = jdbc.queryForObject(
                "SELECT id FROM community_kind WHERE organization_id = :org AND label = 'Area Guild'",
                Map.of("org", ORG), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, "
                        + "updated_at, version) VALUES (:org, :slug, :slug, :kindId, '', 'ACTIVE', now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", ORG, "slug", SLUG, "kindId", kindId), Long.class);
    }
}
