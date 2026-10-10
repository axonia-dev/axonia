package dev.axonia.agent.adapter.in.mcp;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.agent.application.ToolCallRecorder;
import dev.axonia.agent.application.port.out.ToolCallAudit;
import dev.axonia.agent.domain.AgentScope;
import dev.axonia.agent.domain.ToolCall;
import dev.axonia.shared.api.error.ActionableForbiddenException;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class AgentToolSupportTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-07T09:00:00Z"), ZoneOffset.UTC);

    @Mock
    ToolCallAudit audit;

    @Mock
    Supplier<String> body;

    AgentToolSupport sut;

    @BeforeEach
    void setUp() {
        sut = new AgentToolSupport(new AgentScopeGate(), new ToolCallRecorder(audit, CLOCK));
        actWith("u-1", "default", 7L, "read", "topic:propose");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void actWith(String sub, String org, long tokenId, String... scopes) {
        var jwt = Jwt.withTokenValue("t").header("alg", "none").subject(sub).claim("org", org)
                .claim("scopes", List.of(scopes)).claim("tokenId", tokenId).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @Test
    void run_executesAndAuditsOk_whenScopedAndSuccessful() {
        var result = sut.run("topic.propose", AgentScope.TOPIC_PROPOSE, () -> "done");

        assertThat(result).isEqualTo("done");
        var captor = ArgumentCaptor.forClass(ToolCall.class);
        verify(audit).record(captor.capture());
        var row = captor.getValue();
        assertThat(row.toolName()).isEqualTo("topic.propose");
        assertThat(row.outcome()).isEqualTo("OK");
        assertThat(row.userId()).isEqualTo("u-1");
        assertThat(row.tokenId()).isEqualTo(7L);
        assertThat(row.connectionId()).isNull();
        assertThat(row.occurredAt()).isEqualTo(Instant.parse("2026-07-07T09:00:00Z"));
    }

    @Test
    void run_auditsTheConnectionThatMadeTheCall() {

        // given
        var jwt = Jwt.withTokenValue("t").header("alg", "none").subject("u-1").claim("org", "default")
                .claim("scopes", List.of("topic:propose")).claim("connectionId", 4L).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        // when
        sut.run("topic.propose", AgentScope.TOPIC_PROPOSE, () -> "done");

        // then
        var captor = ArgumentCaptor.forClass(ToolCall.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().connectionId()).isEqualTo(4L);
        assertThat(captor.getValue().tokenId()).isNull();
    }

    @Test
    void run_deniesAndAuditsScopeDenied_whenScopeMissing() {
        assertThatThrownBy(() -> sut.run("library.contribute", AgentScope.LIBRARY_CONTRIBUTE, body))
                .isInstanceOf(AgentToolException.class).hasMessageContaining("library:contribute");
        verifyNoInteractions(body);
        var captor = ArgumentCaptor.forClass(ToolCall.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo("SCOPE_DENIED");
    }

    @Test
    void run_mapsDomainExceptionToCleanError_andAuditsDomainError() {
        assertThatThrownBy(() -> sut.run("attendance.signal", AgentScope.TOPIC_PROPOSE, () -> {
            throw new ConflictException("ALREADY_GOING", "You are already going to this session");
        })).isInstanceOf(AgentToolException.class).hasMessageContaining("already going");
        var captor = ArgumentCaptor.forClass(ToolCall.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo("DOMAIN_ERROR");
    }

    @Test
    void run_mapsForbiddenToMembershipMessage() {
        assertThatThrownBy(() -> sut.run("topic.propose", AgentScope.TOPIC_PROPOSE, () -> {
            throw new ForbiddenException("Community membership required");
        })).isInstanceOf(AgentToolException.class).hasMessageContaining("member");
    }

    /**
     * The sanitizer above is the default and must stay so, but it is exactly wrong for a refusal that already says what
     * to change: an agent told "you are not a member of this community" after crediting a colleague would rationally
     * call {@code community.join} on a community it is already in. The subtype is what buys the exception through.
     */
    @Test
    void run_passesAnActionableForbiddenThrough_ratherThanFlatteningItToNotAMember() {
        assertThatThrownBy(() -> sut.run("library.contribute", AgentScope.TOPIC_PROPOSE, () -> {
            throw new ActionableForbiddenException("Only an organizer, or a member the community lets credit "
                    + "contributions, may credit a contribution to another member. Omit contributor to record this "
                    + "contribution as your own.");
        })).isInstanceOf(AgentToolException.class)
                .hasMessage("Only an organizer, or a member the community lets credit contributions, may credit a "
                        + "contribution to another member. Omit contributor to record this contribution as your own.")
                .hasMessageNotContaining("not a member");
        var captor = ArgumentCaptor.forClass(ToolCall.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo("DOMAIN_ERROR");
    }

    @Test
    void run_mapsUnmappedExceptionToGenericMessage_withoutLeakingInternals() {
        assertThatThrownBy(() -> sut.run("topic.propose", AgentScope.TOPIC_PROPOSE, () -> {
            throw new IllegalStateException("leaky internal detail");
        })).isInstanceOf(AgentToolException.class).hasMessage("The action could not be completed.")
                .hasMessageNotContaining("leaky internal detail");
        var captor = ArgumentCaptor.forClass(ToolCall.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo("DOMAIN_ERROR");
    }

    @Test
    void run_mapsNotFoundToItemMessage_andAuditsDomainError() {
        assertThatThrownBy(() -> sut.run("topic.propose", AgentScope.TOPIC_PROPOSE, () -> {
            throw new NotFoundException("Topic 42 does not exist");
        })).isInstanceOf(AgentToolException.class).hasMessage("That item was not found.");
        var captor = ArgumentCaptor.forClass(ToolCall.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo("DOMAIN_ERROR");
    }

    @Test
    void run_mapsValidationToItsOwnMessage_andAuditsDomainError() {
        assertThatThrownBy(() -> sut.run("topic.propose", AgentScope.TOPIC_PROPOSE, () -> {
            throw new ValidationException("BLANK_TITLE", "Title must not be blank");
        })).isInstanceOf(AgentToolException.class).hasMessage("Title must not be blank");
        var captor = ArgumentCaptor.forClass(ToolCall.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo("DOMAIN_ERROR");
    }

    @Test
    void run_wrapsUnexpectedScopeGateFailure_andAuditsIt() {

        // given - a gate that fails with something other than ForbiddenException
        var failingGate = org.mockito.Mockito.mock(AgentScopeGate.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("gate wiring broke")).when(failingGate)
                .require(org.mockito.ArgumentMatchers.any());
        var sutWithFailingGate = new AgentToolSupport(failingGate, new ToolCallRecorder(audit, CLOCK));

        // when / then - the failure is wrapped, leaks nothing, and lands in the audit trail
        assertThatThrownBy(() -> sutWithFailingGate.run("topic.propose", AgentScope.TOPIC_PROPOSE, body))
                .isInstanceOf(AgentToolException.class).hasMessage("The action could not be completed.")
                .hasMessageNotContaining("gate wiring broke");
        verifyNoInteractions(body);
        var captor = ArgumentCaptor.forClass(ToolCall.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo("SCOPE_ERROR");
    }

    @Test
    void run_returnsResult_whenAuditSaveFails() {
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(audit).record(any());

        var result = sut.run("topic.propose", AgentScope.TOPIC_PROPOSE, () -> "done");

        assertThat(result).isEqualTo("done");
    }

    @Test
    void run_recordsNullTokenId_whenClaimAbsent() {
        var jwt = Jwt.withTokenValue("t").header("alg", "none").subject("u-1").claim("org", "default")
                .claim("scopes", List.of("topic:propose")).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        sut.run("topic.propose", AgentScope.TOPIC_PROPOSE, () -> "done");

        var captor = ArgumentCaptor.forClass(ToolCall.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().tokenId()).isNull();
    }
}
