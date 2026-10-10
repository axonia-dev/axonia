package dev.axonia.identity.adapter.in.scim;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.identity.application.scim.ScimTokenAdminService;
import dev.axonia.identity.application.scim.ScimTokenView;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = ScimTokenAdminController.class)
class ScimTokenAdminControllerTest extends ControllerTestSupport {

    @MockitoBean
    ScimTokenAdminService service;

    @Test
    void generate_returns201WithToken() {

        // given
        when(service.generate(eq("my-token"), eq(Instant.parse("2027-01-01T00:00:00Z")))).thenReturn("scim_abc123");

        // when
        var response = mvc.post().uri("/axonia/scim-tokens").contentType(MediaType.APPLICATION_JSON).content("""
                {"label":"my-token","expiresAt":"2027-01-01T00:00:00Z"}""");

        // then
        response.assertThat().hasStatus(201).bodyJson().extractingPath("$.token").isEqualTo("scim_abc123");
    }

    @Test
    void generate_withExpiresAt_passesInstantToService() {

        // given
        when(service.generate(eq("my-token"), any())).thenReturn("scim_exp");

        // when
        var response = mvc.post().uri("/axonia/scim-tokens").contentType(MediaType.APPLICATION_JSON).content("""
                {"label":"my-token","expiresAt":"2026-12-31T00:00:00Z"}""");

        // then
        response.assertThat().hasStatus(201);
        verify(service).generate(eq("my-token"), eq(Instant.parse("2026-12-31T00:00:00Z")));
    }

    @Test
    void generate_forbidden_returns403() {

        // given
        doThrow(new ForbiddenException("no")).when(service).generate(any(), any());

        // when
        var response = mvc.post().uri("/axonia/scim-tokens").contentType(MediaType.APPLICATION_JSON).content("""
                {"label":"my-token","expiresAt":"2027-01-01T00:00:00Z"}""");

        // then
        response.assertThat().hasStatus(403);
    }

    /** A token that has already expired authorises nothing; accepting one only makes the list confusing. */
    @Test
    void generateRefusesAnExpiryInThePast() {

        var response = mvc.post().uri("/axonia/scim-tokens").contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("label", "CI", "expiresAt", "2020-01-01T00:00:00Z")));

        response.assertThat().hasStatus(400);
        verifyNoInteractions(service);
    }

    @Test
    void generateRefusesAnUnboundedLabel() {

        var response = mvc.post().uri("/axonia/scim-tokens").contentType(MediaType.APPLICATION_JSON).content(
                jsonMapper.writeValueAsString(Map.of("label", "x".repeat(1000), "expiresAt", "2027-01-01T00:00:00Z")));

        response.assertThat().hasStatus(400);
        verifyNoInteractions(service);
    }

    @Test
    void generateRefusesAMissingExpiry() {

        var response = mvc.post().uri("/axonia/scim-tokens").contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("label", "CI")));

        response.assertThat().hasStatus(400);
        verifyNoInteractions(service);
    }

    @Test
    void revoke_byId_returns204() {

        // when
        var response = mvc.delete().uri("/axonia/scim-tokens/42");

        // then
        response.assertThat().hasStatus(204);
        verify(service).revoke(42L);
    }

    @Test
    void revoke_notFound_returns404() {

        // given
        doThrow(new NotFoundException("nope")).when(service).revoke(99L);

        // when
        var response = mvc.delete().uri("/axonia/scim-tokens/99");

        // then
        response.assertThat().hasStatus(404);
    }

    @Test
    void revoke_forbidden_returns403() {

        // given
        doThrow(new ForbiddenException("no")).when(service).revoke(any());

        // when
        var response = mvc.delete().uri("/axonia/scim-tokens/42");

        // then
        response.assertThat().hasStatus(403);
    }

    @Test
    void list_returns200WithMetadata() {

        // given
        when(service.list()).thenReturn(List.of(new ScimTokenView(1L, "my-token", null, null, null, "ACTIVE")));

        // when
        var response = mvc.get().uri("/axonia/scim-tokens");

        // then
        response.assertThat().hasStatusOk();
        verify(service).list();
    }

    @Test
    void list_forbidden_returns403() {

        // given
        doThrow(new ForbiddenException("no")).when(service).list();

        // when
        var response = mvc.get().uri("/axonia/scim-tokens");

        // then
        response.assertThat().hasStatus(403);
    }
}
