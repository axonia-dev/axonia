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
import dev.axonia.expertise.api.TagUsageReport;
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
}
