package dev.axonia.agent.adapter.in.mcp;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.agent.application.ToolCallRecorder;
import dev.axonia.agent.application.port.out.ToolCallAudit;
import dev.axonia.expertise.api.TagCuration;
import dev.axonia.expertise.api.TagMergeSuggestion;
import dev.axonia.expertise.api.TagSplit;
import dev.axonia.expertise.api.TagUsageReport;
import dev.axonia.expertise.api.VocabularyTag;
import dev.axonia.shared.api.error.ForbiddenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class CurationToolsTest {

    @Mock
    TagCuration tagCuration;
    @Mock
    ToolCallAudit auditRepo;

    CurationTools sut;

    @BeforeEach
    void setUp() {
        var support = new AgentToolSupport(new AgentScopeGate(),
                new ToolCallRecorder(auditRepo, Clock.fixed(Instant.parse("2026-11-03T09:00:00Z"), ZoneOffset.UTC)));
        sut = new CurationTools(support, tagCuration);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWithScopes(String... scopes) {
        var jwt = Jwt.withTokenValue("t").header("alg", "none").subject("u-1").claim("org", "default")
                .claim("scopes", List.of(scopes)).claim("tokenId", 7L).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @Test
    void tagUsage() {
        authenticateWithScopes("read");
        when(tagCuration.usage("backend-guild", "kafka")).thenReturn(new TagUsageReport(1, 2, 3, 4, 5));

        var actual = sut.tagUsage("backend-guild", "kafka");

        assertThat(actual).isEqualTo(new CurationTools.TagUsageView("kafka", 1, 2, 3, 4, 5));
    }

    @Test
    void tagDelete() {
        authenticateWithScopes("tag:delete");

        var actual = sut.tagDelete("backend-guild", "kafka");

        assertThat(actual).isEqualTo(new CurationTools.DeletedTagView("kafka"));
        verify(tagCuration).delete("backend-guild", "kafka");
    }

    @Test
    void tagDelete_refusedWithReadOnly() {
        authenticateWithScopes("read");

        assertThatThrownBy(() -> sut.tagDelete("backend-guild", "kafka")).isInstanceOf(AgentToolException.class)
                .hasMessageContaining("tag:delete");
        verifyNoInteractions(tagCuration);
    }

    @Test
    void tagDelete_refusedForANonCurator() {
        authenticateWithScopes("tag:delete");
        doThrow(new ForbiddenException("Only a curator may do this")).when(tagCuration).delete("backend-guild",
                "kafka");

        assertThatThrownBy(() -> sut.tagDelete("backend-guild", "kafka")).isInstanceOf(AgentToolException.class);
    }

    @Test
    void vocabulary_listsEachTagWithItsVariantsAndNamedArea() {
        authenticateWithScopes("read");
        when(tagCuration.vocabulary("backend-guild")).thenReturn(List.of(
                new VocabularyTag("kafka", List.of("kafak"), 3, new VocabularyTag.NamedAreaRef(5L, "Messaging")),
                new VocabularyTag("helm", List.of(), 1, null)));

        var actual = sut.vocabulary("backend-guild");

        assertThat(actual).containsExactly(
                new CurationTools.VocabularyTagView("kafka", List.of("kafak"), 3,
                        new CurationTools.NamedAreaRefView(5L, "Messaging")),
                new CurationTools.VocabularyTagView("helm", List.of(), 1, null));
    }

    @Test
    void suggestions_carryWhatEachTagHoldsUp() {
        authenticateWithScopes("read");
        var usage = new TagUsageReport(4, 0, 1, 2, 0);
        when(tagCuration.suggestions("backend-guild"))
                .thenReturn(List.of(new TagMergeSuggestion(new TagMergeSuggestion.Side("kafka", List.of(), 3, usage),
                        new TagMergeSuggestion.Side("kafak", List.of(), 0, new TagUsageReport(1, 0, 0, 0, 0)), 0.83)));

        var actual = sut.suggestions("backend-guild");

        assertThat(actual).singleElement().satisfies(suggestion -> {
            assertThat(suggestion.similarity()).isEqualTo(0.83);
            assertThat(suggestion.tagA()).isEqualTo(new CurationTools.SuggestedTagView("kafka", List.of(), 3,
                    new CurationTools.TagUsageView("kafka", 4, 0, 1, 2, 0)));
            assertThat(suggestion.tagB().usage().artifacts()).isEqualTo(1);
        });
    }

    @Test
    void merge_answersTheCanonicalWithItsVariants() {
        authenticateWithScopes("tag:curate");
        when(tagCuration.merge("backend-guild", List.of("k8s", "kube"), "kubernetes"))
                .thenReturn(new VocabularyTag("kubernetes", List.of("k8s", "kube"), 2, null));

        var actual = sut.merge("backend-guild", List.of("k8s", "kube"), "kubernetes");

        assertThat(actual)
                .isEqualTo(new CurationTools.VocabularyTagView("kubernetes", List.of("k8s", "kube"), 2, null));
    }

    @Test
    void merge_refusedWithoutTheCurateScope_evenWithTagDelete() {
        authenticateWithScopes("read", "tag:delete");

        assertThatThrownBy(() -> sut.merge("backend-guild", List.of("k8s"), "kubernetes"))
                .isInstanceOf(AgentToolException.class).hasMessageContaining("tag:curate");
        verifyNoInteractions(tagCuration);
    }

    @Test
    void merge_refusedForANonCurator() {
        authenticateWithScopes("tag:curate");
        when(tagCuration.merge("backend-guild", List.of("k8s"), "kubernetes"))
                .thenThrow(new ForbiddenException("Tag curation is not open to you"));

        assertThatThrownBy(() -> sut.merge("backend-guild", List.of("k8s"), "kubernetes"))
                .isInstanceOf(AgentToolException.class).hasMessageContaining("not permitted");
    }

    @Test
    void split_answersBothTags() {
        authenticateWithScopes("tag:curate");
        when(tagCuration.split("backend-guild", "k8s")).thenReturn(new TagSplit(
                new VocabularyTag("k8s", List.of(), 1, null), new VocabularyTag("kubernetes", List.of(), 2, null)));

        var actual = sut.split("backend-guild", "k8s");

        assertThat(actual)
                .isEqualTo(new CurationTools.SplitView(new CurationTools.VocabularyTagView("k8s", List.of(), 1, null),
                        new CurationTools.VocabularyTagView("kubernetes", List.of(), 2, null)));
    }

    @Test
    void split_ofATagNotMerged_hasNoFormerCanonical() {
        authenticateWithScopes("tag:curate");
        when(tagCuration.split("backend-guild", "helm")).thenReturn(new TagSplit(VocabularyTag.bare("helm"), null));

        assertThat(sut.split("backend-guild", "helm").formerCanonical()).isNull();
    }

    @Test
    void rename_answersTheTagUnderItsNewName() {
        authenticateWithScopes("tag:curate");
        when(tagCuration.rename("backend-guild", "kafka", "Event Streaming"))
                .thenReturn(new VocabularyTag("event-streaming", List.of(), 3, null));

        var actual = sut.rename("backend-guild", "kafka", "Event Streaming");

        assertThat(actual.tag()).isEqualTo("event-streaming");
    }

    @Test
    void dismissAndUndismiss_answerThePairAndWhetherItIsParked() {
        authenticateWithScopes("tag:curate");

        assertThat(sut.dismissSuggestion("backend-guild", "kafka", "kafak"))
                .isEqualTo(new CurationTools.SuggestionPairView("kafka", "kafak", true));
        assertThat(sut.undismissSuggestion("backend-guild", "kafka", "kafak"))
                .isEqualTo(new CurationTools.SuggestionPairView("kafka", "kafak", false));
        verify(tagCuration).dismiss("backend-guild", "kafka", "kafak");
        verify(tagCuration).undismiss("backend-guild", "kafka", "kafak");
    }
}
