package dev.axonia.identity.adapter.in.scim;

import java.io.PrintWriter;
import java.io.StringWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.identity.application.scim.ScimPrincipal;
import dev.axonia.identity.application.scim.ScimTokens;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScimTokenAuthenticationFilterTest {

    @Mock
    ScimTokens scimTokens;

    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse response;

    @Mock
    FilterChain filterChain;

    @Test
    void blankBearerToken_returns401AndShortsCircuit() throws Exception {

        // given
        // "Bearer " followed only by whitespace → token.isBlank() = true → 401, no further filter
        ScimTokenAuthenticationFilter sut = new ScimTokenAuthenticationFilter(scimTokens, JsonMapper.builder().build());

        when(request.getHeader("Authorization")).thenReturn("Bearer   ");
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        // when
        sut.doFilter(request, response, filterChain);

        // then
        verify(response).setStatus(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void validToken_setsAuthenticationAndContinuesChain() throws Exception {

        // given
        ScimTokenAuthenticationFilter sut = new ScimTokenAuthenticationFilter(scimTokens, JsonMapper.builder().build());
        var principal = new ScimPrincipal("default");
        when(request.getHeader("Authorization")).thenReturn("Bearer scim_valid");
        when(scimTokens.authenticate("scim_valid")).thenReturn(java.util.Optional.of(principal));

        // when
        sut.doFilter(request, response, filterChain);

        // then
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void validToken_recordsUsage_afterSecurityContextSet_beforeChain() throws Exception {

        // given
        ScimTokenAuthenticationFilter sut = new ScimTokenAuthenticationFilter(scimTokens, JsonMapper.builder().build());
        var principal = new ScimPrincipal("default");
        when(request.getHeader("Authorization")).thenReturn("Bearer scim_valid");
        when(scimTokens.authenticate("scim_valid")).thenReturn(java.util.Optional.of(principal));

        // when
        sut.doFilter(request, response, filterChain);

        // then — usage is recorded with the principal's org + raw token, and ordered before the chain
        var inOrder = org.mockito.Mockito.inOrder(scimTokens, filterChain);
        inOrder.verify(scimTokens).recordUsage("default", "scim_valid");
        inOrder.verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidToken_doesNotRecordUsage() throws Exception {

        // given — empty principal → 401, usage must NOT be recorded
        ScimTokenAuthenticationFilter sut = new ScimTokenAuthenticationFilter(scimTokens, JsonMapper.builder().build());
        when(request.getHeader("Authorization")).thenReturn("Bearer scim_bad");
        when(scimTokens.authenticate("scim_bad")).thenReturn(java.util.Optional.empty());
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        // when
        sut.doFilter(request, response, filterChain);

        // then
        verify(response).setStatus(401);
        verify(scimTokens, never()).recordUsage(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void scimAuthenticationToken_getCredentials_returnsNull() {

        // given
        // getCredentials() returns null because the bearer token is not stored after authentication
        var principal = new ScimPrincipal("tenant-x");
        var token = new ScimAuthenticationToken(principal);

        // then
        assertThat(token.getCredentials()).isNull();
        assertThat(token.getPrincipal()).isSameAs(principal);
        assertThat(token.organizationId()).isEqualTo("tenant-x");
    }
}
