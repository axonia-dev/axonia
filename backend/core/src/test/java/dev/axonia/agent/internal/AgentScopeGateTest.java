package dev.axonia.agent.internal;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.ForbiddenException;
import dev.axonia.agent.AgentScope;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentScopeGateTest {

    private final AgentScopeGate sut = new AgentScopeGate();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void actWithScopes(String... scopes) {
        var jwt = Jwt.withTokenValue("t").header("alg", "none").subject("u").claim("org", "default")
                .claim("scopes", List.of(scopes)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @Test
    void require_passesWhenScopePresent() {
        actWithScopes("read", "topic:propose");
        assertThatCode(() -> sut.require(AgentScope.TOPIC_PROPOSE)).doesNotThrowAnyException();
    }

    @Test
    void require_throwsWhenScopeMissing() {
        actWithScopes("read");
        assertThatThrownBy(() -> sut.require(AgentScope.LIBRARY_CONTRIBUTE)).isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("library:contribute");
    }

    @Test
    void require_throwsWhenNoScopesClaim() {
        var jwt = Jwt.withTokenValue("t").header("alg", "none").subject("u").claim("org", "default").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        assertThatThrownBy(() -> sut.require(AgentScope.READ)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void require_throwsWhenNoAuthentication() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> sut.require(AgentScope.READ)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void require_passesForSessionAttachWhenGranted() {
        actWithScopes("read", "session:attach");
        assertThatCode(() -> sut.require(AgentScope.SESSION_ATTACH)).doesNotThrowAnyException();
    }

    @Test
    void require_throwsWhenSessionAttachMissing() {
        actWithScopes("read");
        assertThatThrownBy(() -> sut.require(AgentScope.SESSION_ATTACH)).isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("session:attach");
    }

    @Test
    void require_throwsWhenSessionDetachMissing_evenWithAttach() {
        actWithScopes("read", "session:attach");
        assertThatThrownBy(() -> sut.require(AgentScope.SESSION_DETACH)).isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("session:detach");
    }
}
