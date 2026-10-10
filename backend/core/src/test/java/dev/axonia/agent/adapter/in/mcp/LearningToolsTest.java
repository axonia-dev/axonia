package dev.axonia.agent.adapter.in.mcp;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.agent.application.ToolCallRecorder;
import dev.axonia.agent.application.port.out.ToolCallAudit;
import dev.axonia.agent.domain.ToolCall;
import dev.axonia.learning.api.HeldArea;
import dev.axonia.learning.api.Learner;
import dev.axonia.learning.api.LearningIntents;
import dev.axonia.learning.api.LearningPage;
import dev.axonia.learning.api.WantedArea;
import dev.axonia.shared.api.error.ForbiddenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class LearningToolsTest {

    private static final Instant EARLY = Instant.parse("2026-09-01T09:00:00Z");

    @Mock
    LearningIntents learning;
    @Mock
    ToolCallAudit auditRepo;

    LearningTools sut;

    @BeforeEach
    void setUp() {
        var support = new AgentToolSupport(new AgentScopeGate(),
                new ToolCallRecorder(auditRepo, Clock.fixed(Instant.parse("2026-09-25T09:00:00Z"), ZoneOffset.UTC)));
        sut = new LearningTools(support, learning);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** Replaces the SecurityContext with a token carrying exactly the given scope codes. */
    private void authenticateWithScopes(String... scopes) {
        var jwt = Jwt.withTokenValue("t").header("alg", "none").subject("u-1").claim("org", "default")
                .claim("scopes", List.of(scopes)).claim("tokenId", 7L).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @Test
    void demandListsTheWantedAreasAndFlagsTheOnesHeld() {

        // given
        authenticateWithScopes("read");
        when(learning.page("platform"))
                .thenReturn(new LearningPage(true, false, 10, List.of(new HeldArea("kafka", EARLY)),
                        List.of(new WantedArea("kafka", 4, 1), new WantedArea("rust", 2, 0))));

        // when
        var actual = sut.demand("platform");

        // then
        assertThat(actual).containsExactly(new LearningTools.DemandView("kafka", 4, 1, true),
                new LearningTools.DemandView("rust", 2, 0, false));
    }

    @Test
    void learnersNeedTheRosterScope() {

        // given
        authenticateWithScopes("read");

        // when / then
        assertThatThrownBy(() -> sut.learners("platform", "kafka")).isInstanceOf(AgentToolException.class)
                .hasMessageContaining("members:read");
        verifyNoInteractions(learning);
    }

    @Test
    void learnersNameEachLearnerAndTheirDate() {

        // given
        authenticateWithScopes("members:read");
        when(learning.learners("platform", "kafka")).thenReturn(List.of(new Learner("alice", "Alice", null, EARLY)));

        // when
        var actual = sut.learners("platform", "kafka");

        // then
        assertThat(actual).containsExactly(new LearningTools.LearnerView("alice", "Alice", EARLY));
    }

    @Test
    void addDelegatesUnderTheDeclareScope() {

        // given
        authenticateWithScopes("learning:declare");
        when(learning.add("platform", "Kafka")).thenReturn(new HeldArea("kafka", EARLY));

        // when
        var actual = sut.add("platform", "Kafka");

        // then
        assertThat(actual).isEqualTo(new LearningTools.HeldView("kafka", EARLY));
    }

    @Test
    void removeDelegatesUnderTheSameScopeAndAnswersTheAreaItRemoved() {

        // given
        authenticateWithScopes("learning:declare");
        when(learning.remove("platform", "K8s")).thenReturn("kubernetes");

        // when
        var actual = sut.remove("platform", "K8s");

        // then
        assertThat(actual).isEqualTo(new LearningTools.RemovedView("kubernetes"));
    }

    @Test
    void writesAreRefusedWithoutTheDeclareScope() {

        // given
        authenticateWithScopes("read", "members:read");

        // when / then
        assertThatThrownBy(() -> sut.add("platform", "kafka")).isInstanceOf(AgentToolException.class)
                .hasMessageContaining("learning:declare");
        assertThatThrownBy(() -> sut.remove("platform", "kafka")).isInstanceOf(AgentToolException.class)
                .hasMessageContaining("learning:declare");
        verifyNoInteractions(learning);
    }

    @Test
    void aNonMembersAddIsTheServicesRefusal_toldAsNotAMember_andAuditedAsADomainError() {

        // given
        authenticateWithScopes("learning:declare");
        when(learning.add("platform", "kafka")).thenThrow(new ForbiddenException("Community membership required"));

        // when / then
        assertThatThrownBy(() -> sut.add("platform", "kafka")).isInstanceOf(AgentToolException.class)
                .hasMessage("You are not a member of this community, or not permitted to do this.");
        assertThat(auditedOutcomeOf("learning.add")).isEqualTo("DOMAIN_ERROR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"learning.demand", "learning.learners", "learning.add", "learning.remove"})
    void aSuccessfulCallIsAuditedOnceUnderItsOwnName(String tool) {

        // given
        authenticateWithScopes("read", "members:read", "learning:declare");
        stubSuccessFor(tool);

        // when
        invoke(tool).accept(sut);

        // then
        assertThat(auditedOutcomeOf(tool)).isEqualTo("OK");
    }

    @ParameterizedTest
    @ValueSource(strings = {"learning.demand", "learning.learners", "learning.add", "learning.remove"})
    void aScopeRefusalIsAuditedOnceUnderItsOwnName(String tool) {

        // given
        authenticateWithScopes();

        // when / then
        assertThatThrownBy(() -> invoke(tool).accept(sut)).isInstanceOf(AgentToolException.class);
        assertThat(auditedOutcomeOf(tool)).isEqualTo("SCOPE_DENIED");
        verifyNoInteractions(learning);
    }

    /** Exactly one audit row was saved, and it names this tool; its outcome is returned for the caller to assert. */
    private String auditedOutcomeOf(String tool) {
        var captor = ArgumentCaptor.forClass(ToolCall.class);
        verify(auditRepo).record(captor.capture());
        assertThat(captor.getValue().toolName()).isEqualTo(tool);
        return captor.getValue().outcome();
    }

    private void stubSuccessFor(String tool) {
        switch (tool) {
            case "learning.demand" ->
                when(learning.page("platform")).thenReturn(new LearningPage(true, false, 10, List.of(), List.of()));
            case "learning.learners" -> when(learning.learners("platform", "kafka")).thenReturn(List.of());
            case "learning.add" -> when(learning.add("platform", "kafka")).thenReturn(new HeldArea("kafka", EARLY));
            default -> {
            }
        }
    }

    private static Consumer<LearningTools> invoke(String tool) {
        return switch (tool) {
            case "learning.demand" -> tools -> tools.demand("platform");
            case "learning.learners" -> tools -> tools.learners("platform", "kafka");
            case "learning.add" -> tools -> tools.add("platform", "kafka");
            case "learning.remove" -> tools -> tools.remove("platform", "kafka");
            default -> throw new IllegalArgumentException("Unknown learning tool: " + tool);
        };
    }
}
