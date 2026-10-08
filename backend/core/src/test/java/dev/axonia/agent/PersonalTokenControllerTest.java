package dev.axonia.agent;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.identity.pat.MintPersonalAccessToken;
import dev.axonia.identity.pat.PersonalAccessTokens;
import dev.axonia.identity.pat.PersonalTokenSummary;
import dev.axonia.identity.pat.UpdatePersonalAccessToken;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@CoreWebMvcTest(controllers = PersonalTokenController.class)
class PersonalTokenControllerTest extends ControllerTestSupport {

    @MockitoBean
    private PersonalAccessTokens tokens;

    @MockitoBean
    private AgentAccess access;

    @BeforeEach
    void agentSurfaceIsAvailable() {
        when(access.isAvailableToCaller()).thenReturn(true);
    }

    @Test
    void mint_returnsPlaintextOnce_andDelegatesToPort() {

        // given
        when(tokens.mint(any(MintPersonalAccessToken.class))).thenReturn("axp_secret123");

        // when
        var actual = mvc.post().uri("/axonia/me/tokens").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"cli\",\"scopes\":[\"read\",\"topic:propose\"],"
                        + "\"expiresAt\":\"2027-01-01T00:00:00Z\"}");

        // then
        actual.assertThat().hasStatus(201).bodyJson().extractingPath("$.token").isEqualTo("axp_secret123");
        verify(tokens).mint(new MintPersonalAccessToken(DEFAULT_TEST_USER, "default", "cli",
                Set.of("read", "topic:propose"), Instant.parse("2027-01-01T00:00:00Z")));
    }

    /**
     * An expiry is optional, and an absent one reaches the port as null, which it reads as lasting until revoked.
     * Revocation is the control this surface relies on, and it takes effect immediately.
     */
    @Test
    void mint_acceptsAnAbsentExpiry() {

        // given
        when(tokens.mint(any(MintPersonalAccessToken.class))).thenReturn("axp_secret123");

        // when
        var actual = mvc.post().uri("/axonia/me/tokens").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"cli\",\"scopes\":[\"read\"]}");

        // then
        actual.assertThat().hasStatus(201).bodyJson().extractingPath("$.token").isEqualTo("axp_secret123");
        verify(tokens).mint(new MintPersonalAccessToken(DEFAULT_TEST_USER, "default", "cli", Set.of("read"), null));
    }

    /** A token that has already expired authorises nothing; accepting one only makes the list confusing. */
    @Test
    void mint_refusesAnExpiryInThePast() {

        // when
        var actual = mvc.post().uri("/axonia/me/tokens").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"cli\",\"scopes\":[\"read\"],\"expiresAt\":\"2020-01-01T00:00:00Z\"}");

        // then
        actual.assertThat().hasStatus(400);
        verifyNoInteractions(tokens);
    }

    @Test
    void mint_refusesAnUnboundedName() {

        // when
        var actual = mvc.post().uri("/axonia/me/tokens").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + "x".repeat(200) + "\",\"scopes\":[]}");

        // then
        actual.assertThat().hasStatus(400);
        verifyNoInteractions(tokens);
    }

    @Test
    void mint_rejectsUnknownScope() {

        // when
        var actual = mvc.post().uri("/axonia/me/tokens").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"cli\",\"scopes\":[\"delete:everything\"]}");

        // then
        actual.assertThat().hasStatus(400);
        verifyNoInteractions(tokens);
    }

    @Test
    void list_returnsSummaries_withNoSecret() {

        // given
        when(tokens.list(DEFAULT_TEST_USER, "default")).thenReturn(List.of(new PersonalTokenSummary(1L, "cli",
                Set.of("read"), null, null, Instant.parse("2026-07-01T00:00:00Z"), "ACTIVE")));

        // when
        var actual = mvc.get().uri("/axonia/me/tokens");

        // then
        actual.assertThat().hasStatusOk().bodyJson().extractingPath("$[0].name").isEqualTo("cli");
        actual.assertThat().bodyText().doesNotContain("axp_");
    }

    @Test
    void revoke_delegatesToPort_scopedToOwner() {

        // when
        var actual = mvc.delete().uri("/axonia/me/tokens/{id}", 5L);

        // then
        actual.assertThat().hasStatus(204);
        verify(tokens).revoke(DEFAULT_TEST_USER, "default", 5L);
    }

    @Test
    void mint_actsAsTheCallingMember_notADifferentSubject() {

        // given
        when(access.isAvailableToCaller()).thenReturn(true);
        when(tokens.mint(any(MintPersonalAccessToken.class))).thenReturn("axp_other");

        // when
        var actual = mvc.post().uri("/axonia/me/tokens")
                .with(jwt().jwt(token -> token.subject("other-user").claim("org", "acme")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"cli\",\"scopes\":[],\"expiresAt\":\"2027-01-01T00:00:00Z\"}");

        // then
        actual.assertThat().hasStatus(201);
        verify(tokens).mint(new MintPersonalAccessToken("other-user", "acme", "cli", Set.of(),
                Instant.parse("2027-01-01T00:00:00Z")));
    }

    @Test
    void update_delegatesToPort_scopedToTheCallingMember() {

        // when
        var actual = mvc.patch().uri("/axonia/me/tokens/{id}", 7L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"claude\",\"scopes\":[\"read\",\"topic:propose\"]}");

        // then
        actual.assertThat().hasStatus(204);
        verify(tokens).update(new UpdatePersonalAccessToken(DEFAULT_TEST_USER, "default", 7L, "claude",
                Set.of("read", "topic:propose")));
    }

    @Test
    void update_rejectsUnknownScope() {

        // when
        var actual = mvc.patch().uri("/axonia/me/tokens/{id}", 7L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"claude\",\"scopes\":[\"delete:everything\"]}");

        // then
        actual.assertThat().hasStatus(400);
        verifyNoInteractions(tokens);
    }

    @Test
    void update_rejectsBlankName() {

        // when
        var actual = mvc.patch().uri("/axonia/me/tokens/{id}", 7L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"scopes\":[\"read\"]}");

        // then
        actual.assertThat().hasStatus(400);
        verifyNoInteractions(tokens);
    }

    @Test
    void mint_isRefusedWhereTheAgentSurfaceIsNotAvailable() {

        // given
        when(access.isAvailableToCaller()).thenReturn(false);
        when(access.refusal()).thenReturn("Connecting an agent is not open.");

        // when
        var actual = mvc.post().uri("/axonia/me/tokens").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"cli\",\"scopes\":[\"read\"],\"expiresAt\":\"2027-01-01T00:00:00Z\"}");

        // then
        actual.assertThat().hasStatus(403).bodyJson().extractingPath("$.detail")
                .isEqualTo("Connecting an agent is not open.");
        verifyNoInteractions(tokens);
    }

    @Test
    void update_isRefusedWhereTheAgentSurfaceIsNotAvailable() {

        // given
        when(access.isAvailableToCaller()).thenReturn(false);
        when(access.refusal()).thenReturn("Connecting an agent is not open.");

        // when
        var actual = mvc.patch().uri("/axonia/me/tokens/7").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"cli\",\"scopes\":[\"read\"]}");

        // then
        actual.assertThat().hasStatus(403).bodyJson().extractingPath("$.detail")
                .isEqualTo("Connecting an agent is not open.");
        verifyNoInteractions(tokens);
    }

    @Test
    void revoke_staysOpen_soAStrandedCredentialCanStillBeWithdrawn() {

        // given
        when(access.isAvailableToCaller()).thenReturn(false);

        // when
        var actual = mvc.delete().uri("/axonia/me/tokens/7");

        // then
        actual.assertThat().hasStatus(204);
        verify(tokens).revoke(DEFAULT_TEST_USER, "default", 7L);
    }
}
