package dev.axonia;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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

import dev.axonia.expertise.application.ExpertiseCurationService;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * A declaration carries the spelling it was made under, so it keeps that spelling's merge alive exactly as a signal
 * does. A rename or a delete of the area therefore refuses while a declared variant stands, the curation vocabulary
 * draws the chip that refusal asks for, and the curator splits it first; the split-off declarations then stay on their
 * own spelling. An alias row nothing carries at all is still dead, and is still swept in silence.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class DeclarationCurationIntegrationTest {

    private static final String ORG = "default";
    private static final String CURATOR = "declaration-curator";
    private static final Instant EARLIER = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant LATER = Instant.parse("2026-09-10T09:00:00Z");

    /** A slug and members of their own per test: the fixtures commit, and are only truncated once the class is done. */
    private static final AtomicInteger RUNS = new AtomicInteger();
    private static final AtomicLong SOURCES = new AtomicLong(890_000L);

    @Autowired
    private ExpertiseCurationService curation;

    @Autowired
    private JdbcTemplate jdbc;

    private String slug;
    private long communityId;
    private String bob;
    private String carol;

    @BeforeEach
    void seedACommunityWhoseOnlySignalIsTheCanonicalAndOneAliasOntoIt() {

        var run = RUNS.incrementAndGet();
        slug = "declaration-curation-" + run;
        bob = "declaration-bob-" + run;
        carol = "declaration-carol-" + run;
        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES (?, 'Curation Guild', now(), now(), 0) ON CONFLICT DO NOTHING", ORG);
        communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, description, status, kind_id, created_at, "
                        + "updated_at, version) VALUES (?, ?, ?, '', 'ACTIVE', (SELECT id FROM community_kind "
                        + "WHERE organization_id = ? AND label = 'Curation Guild'), now(), now(), 0) RETURNING id",
                Long.class, ORG, slug, slug, ORG);
        jdbc.update("INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                + "version) VALUES (?, ?, ?, 'ORGANIZER', now(), now(), 0)", ORG, communityId, CURATOR);
        jdbc.update("INSERT INTO expertise_signal (organization_id, community_id, user_id, tag, signal_type, "
                + "source_type, source_id, occurred_at) VALUES (?, ?, ?, 'kubernetes', 'CONTRIBUTED', 'ARTIFACT', "
                + "?, now())", ORG, communityId, CURATOR, 880_000L + run);
        jdbc.update("INSERT INTO expertise_tag_alias (organization_id, community_id, variant_tag, canonical_tag) "
                + "VALUES (?, ?, 'k8s', 'kubernetes')", ORG, communityId);

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(CURATOR).claim("org", ORG).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void renamingSweepsAnAliasNothingCarriesAndTakesTheHiddenAreaWithIt() {

        // given - nobody ever typed "k8s": the alias row outlived every row that read through it
        declareTheArea();

        // when
        curation.rename(slug, "kubernetes", "containers");

        // then
        assertThat(declarations()).extracting("user_id", "tag", "declared_at").containsExactlyInAnyOrder(
                tuple(bob, "containers", Timestamp.from(EARLIER)), tuple(carol, "containers", Timestamp.from(LATER)));
        assertThat(hiddenTags(bob)).containsExactly("containers");
        assertThat(variantsOf("kubernetes")).isEmpty();
    }

    @Test
    void deletingSweepsAnAliasNothingCarriesAndTheHiddenAreaItNamed() {

        // given
        declareTheArea();

        // when
        curation.delete(slug, "kubernetes");

        // then
        assertThat(declarations()).isEmpty();
        assertThat(hiddenTags(bob)).isEmpty();
        assertThat(variantsOf("kubernetes")).isEmpty();
    }

    @Test
    void aDeclaredVariantKeepsTheMergeAliveSoBothDestructiveVerbsRefuse() {

        // given - bob's declaration is the only thing carrying "k8s"
        declareTheVariant();

        // then
        assertThatThrownBy(() -> curation.rename(slug, "kubernetes", "containers"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("'kubernetes' has other tags merged into it. Split them out first.");
        assertThatThrownBy(() -> curation.delete(slug, "kubernetes")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("'kubernetes' has other tags merged into it. Split them out first.");
        assertThatThrownBy(() -> curation.delete(slug, "k8s")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("'k8s' is merged into 'kubernetes'. Split it out first.");
    }

    @Test
    void afterTheSplitBothVerbsRunAndTheDeclarationsStayOnTheirOwnSpelling() {

        // given
        declareTheVariant();

        // when - the curator splits the chip the curation card draws
        curation.split(slug, "k8s");

        // then - bob's hidden area follows his only remaining spelling
        assertThat(hiddenTags(bob)).containsExactly("k8s");

        // when
        curation.rename(slug, "kubernetes", "containers");

        // then - only carol's declaration of the area moves
        assertThat(declarations()).extracting("user_id", "tag", "declared_at").containsExactlyInAnyOrder(
                tuple(bob, "k8s", Timestamp.from(EARLIER)), tuple(carol, "containers", Timestamp.from(LATER)));
        assertThat(hiddenTags(bob)).containsExactly("k8s");

        // when
        curation.delete(slug, "k8s");

        // then
        assertThat(declarations()).extracting("user_id", "tag").containsExactly(tuple(carol, "containers"));
        assertThat(hiddenTags(bob)).isEmpty();
    }

    @Test
    void renamingOntoAMergedTagKeepsItsLiveVariantsSignalsAndDeclarationsReadingAsIt() {

        // given - "kube" carries a signal and a declaration and is merged into "kubernetes"
        declareTheVariant();
        declare(carol, "k8s", EARLIER);
        signal("kube");
        signal("containers");
        jdbc.update("INSERT INTO expertise_tag_alias (organization_id, community_id, variant_tag, canonical_tag) "
                + "VALUES (?, ?, 'kube', 'kubernetes')", ORG, communityId);
        declare(bob, "kube", LATER);

        // when
        curation.rename(slug, "containers", "kubernetes");

        // then
        assertThat(variantsOf("kubernetes")).containsExactlyInAnyOrder("k8s", "kube");
        assertThat(declarations()).extracting("user_id", "tag").containsExactlyInAnyOrder(tuple(bob, "k8s"),
                tuple(bob, "kube"), tuple(carol, "kubernetes"), tuple(carol, "k8s"));
        assertThat(jdbc.queryForList("SELECT tag FROM expertise_signal WHERE organization_id = ? AND community_id = ?",
                String.class, ORG, communityId)).containsExactlyInAnyOrder("kubernetes", "kube", "kubernetes");
    }

    @Test
    void splittingTheVariantMovesTheHiddenAreaOntoItForItsDeclarer() {

        // given - the curator also has evidence under "k8s", dismissed under the area's name
        declareTheVariant();
        signal("k8s");
        jdbc.update("INSERT INTO expertise_dismissed_signal (organization_id, user_id, source_type, source_id, tag) "
                + "VALUES (?, ?, 'ARTIFACT', ?, 'kubernetes')", ORG, CURATOR, SOURCES.get());

        // when
        curation.split(slug, "k8s");

        // then
        assertThat(hiddenTags(bob)).containsExactly("k8s");
        assertThat(jdbc.queryForList("SELECT tag FROM expertise_dismissed_signal WHERE organization_id = ? "
                + "AND user_id = ? AND source_id = ?", String.class, ORG, CURATOR, SOURCES.get()))
                .containsExactlyInAnyOrder("kubernetes", "k8s");
    }

    /** Bob declared the variant spelling and hid the area it reads as; carol declared the area itself. */
    private void declareTheVariant() {

        declare(bob, "k8s", EARLIER);
        hide(bob, "kubernetes");
        declare(carol, "kubernetes", LATER);
    }

    /** Both members declared the area under its own name, so the alias row carries nothing at all. */
    private void declareTheArea() {

        declare(bob, "kubernetes", EARLIER);
        hide(bob, "kubernetes");
        declare(carol, "kubernetes", LATER);
    }

    private void signal(String tag) {

        jdbc.update("INSERT INTO expertise_signal (organization_id, community_id, user_id, tag, signal_type, "
                + "source_type, source_id, occurred_at) VALUES (?, ?, ?, ?, 'CONTRIBUTED', 'ARTIFACT', ?, now())", ORG,
                communityId, CURATOR, tag, SOURCES.incrementAndGet());
    }

    private void declare(String userId, String tag, Instant declaredAt) {

        jdbc.update("INSERT INTO expertise_declaration (organization_id, community_id, user_id, tag, declared_at) "
                + "VALUES (?, ?, ?, ?, ?)", ORG, communityId, userId, tag, Timestamp.from(declaredAt));
    }

    private void hide(String userId, String tag) {

        jdbc.update("INSERT INTO expertise_hidden_tag (organization_id, user_id, tag) VALUES (?, ?, ?)", ORG, userId,
                tag);
    }

    private List<Map<String, Object>> declarations() {

        return jdbc.queryForList("SELECT user_id, tag, declared_at FROM expertise_declaration "
                + "WHERE organization_id = ? AND community_id = ?", ORG, communityId);
    }

    private List<String> hiddenTags(String userId) {

        return jdbc.queryForList("SELECT tag FROM expertise_hidden_tag WHERE organization_id = ? AND user_id = ?",
                String.class, ORG, userId);
    }

    private List<String> variantsOf(String canonical) {

        return jdbc.queryForList("SELECT variant_tag FROM expertise_tag_alias WHERE organization_id = ? "
                + "AND community_id = ? AND canonical_tag = ?", String.class, ORG, communityId, canonical);
    }
}
