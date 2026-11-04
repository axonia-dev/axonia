package dev.axonia.agent.adapter.in.mcp;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

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
import dev.axonia.expertise.api.CuratedNamedArea;
import dev.axonia.expertise.api.NamedAreaAssignment;
import dev.axonia.expertise.api.NamedAreaCuration;
import dev.axonia.expertise.api.TagUnassignment;
import dev.axonia.shared.api.error.ForbiddenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class NamedAreaToolsTest {

    private static final String SLUG = "backend-guild";
    private static final CuratedNamedArea MESSAGING = new CuratedNamedArea(5L, "Messaging", List.of("kafka"));
    private static final NamedAreaTools.NamedAreaView MESSAGING_VIEW = new NamedAreaTools.NamedAreaView(5L, "Messaging",
            List.of("kafka"));

    @Mock
    NamedAreaCuration namedAreaCuration;
    @Mock
    ToolCallAudit auditRepo;

    NamedAreaTools sut;

    @BeforeEach
    void setUp() {
        var support = new AgentToolSupport(new AgentScopeGate(),
                new ToolCallRecorder(auditRepo, Clock.fixed(Instant.parse("2026-11-04T09:00:00Z"), ZoneOffset.UTC)));
        sut = new NamedAreaTools(support, namedAreaCuration);
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
    void list_readsUnderTheReadScope() {
        authenticateWithScopes("read");
        when(namedAreaCuration.namedAreas(SLUG)).thenReturn(List.of(MESSAGING));

        assertThat(sut.list(SLUG)).containsExactly(MESSAGING_VIEW);
    }

    @Test
    void createRenameAndDelete_answerTheArea() {
        authenticateWithScopes("tag:curate");
        when(namedAreaCuration.create(SLUG, "Messaging")).thenReturn(new CuratedNamedArea(5L, "Messaging", List.of()));
        when(namedAreaCuration.rename(SLUG, 5L, "Messaging")).thenReturn(MESSAGING);
        when(namedAreaCuration.delete(SLUG, 5L)).thenReturn(MESSAGING);

        assertThat(sut.create(SLUG, "Messaging"))
                .isEqualTo(new NamedAreaTools.NamedAreaView(5L, "Messaging", List.of()));
        assertThat(sut.rename(SLUG, 5L, "Messaging")).isEqualTo(MESSAGING_VIEW);
        assertThat(sut.delete(SLUG, 5L)).as("the deleted area names the tags it released").isEqualTo(MESSAGING_VIEW);
    }

    @Test
    void assignTags_reportsEveryMove() {
        authenticateWithScopes("tag:curate");
        when(namedAreaCuration.assign(SLUG, 6L, List.of("kafka", "spring")))
                .thenReturn(new NamedAreaAssignment(new CuratedNamedArea(6L, "Backend", List.of("kafka", "spring")),
                        List.of(new NamedAreaAssignment.Move("kafka", 5L, "Messaging"))));

        var actual = sut.assignTags(SLUG, 6L, List.of("kafka", "spring"));

        assertThat(actual).isEqualTo(new NamedAreaTools.AssignmentView(
                new NamedAreaTools.NamedAreaView(6L, "Backend", List.of("kafka", "spring")),
                List.of(new NamedAreaTools.MovedTagView("kafka", 5L, "Messaging"))));
    }

    @Test
    void unassignTag_answersTheAreaItLeft() {
        authenticateWithScopes("tag:curate");
        when(namedAreaCuration.unassign(SLUG, "Spring Boot"))
                .thenReturn(new TagUnassignment("spring", Optional.of(MESSAGING)));

        assertThat(sut.unassignTag(SLUG, "Spring Boot")).as("the answer names the canonical it took out")
                .isEqualTo(new NamedAreaTools.UnassignmentView("spring", MESSAGING_VIEW));
    }

    @Test
    void unassignTag_ofATagNoAreaHeld_hasNoFromArea() {
        authenticateWithScopes("tag:curate");
        when(namedAreaCuration.unassign(SLUG, "spring")).thenReturn(new TagUnassignment("spring", Optional.empty()));

        assertThat(sut.unassignTag(SLUG, "spring")).isEqualTo(new NamedAreaTools.UnassignmentView("spring", null));
    }

    @Test
    void writes_refusedWithOnlyTheReadScope() {
        authenticateWithScopes("read");

        assertThatThrownBy(() -> sut.create(SLUG, "Messaging")).isInstanceOf(AgentToolException.class)
                .hasMessageContaining("tag:curate");
        verifyNoInteractions(namedAreaCuration);
    }

    @Test
    void writes_refusedForANonCurator() {
        authenticateWithScopes("tag:curate");
        when(namedAreaCuration.assign(SLUG, 6L, List.of("kafka")))
                .thenThrow(new ForbiddenException("Tag curation is not open to you"));

        assertThatThrownBy(() -> sut.assignTags(SLUG, 6L, List.of("kafka"))).isInstanceOf(AgentToolException.class)
                .hasMessageContaining("not permitted");
    }
}
