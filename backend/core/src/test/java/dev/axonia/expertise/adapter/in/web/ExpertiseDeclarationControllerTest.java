package dev.axonia.expertise.adapter.in.web;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.expertise.application.ExpertiseDeclarationService;
import dev.axonia.expertise.domain.Declaration;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ValidationException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = ExpertiseDeclarationController.class)
class ExpertiseDeclarationControllerTest extends ControllerTestSupport {

    @MockitoBean
    private ExpertiseDeclarationService expertiseDeclarationService;

    @Test
    void declaringReturnsTheStoredTagAndDate() {

        // given
        when(expertiseDeclarationService.declare("platform", "Kafka")).thenReturn(Declaration.builder().communityId(7L)
                .userId("alice").tag("kafka").declaredAt(Instant.parse("2026-09-12T12:00:00Z")).build());

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/expertise/declarations", "platform")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tag\":\"Kafka\"}").exchange();

        // then
        actual.assertThat().hasStatusOk().bodyJson()
                .isLenientlyEqualTo("{\"tag\": \"kafka\", \"declaredAt\": \"2026-09-12T12:00:00Z\"}");
        verify(expertiseDeclarationService).declare("platform", "Kafka");
    }

    @Test
    void aBlankTagIsABadRequestCarryingTagBlank() {

        // given
        when(expertiseDeclarationService.declare("platform", " !! "))
                .thenThrow(new ValidationException("TAG_BLANK", "A tag needs a name"));

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/expertise/declarations", "platform")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tag\":\" !! \"}").exchange();

        // then
        actual.assertThat().hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("TAG_BLANK");
    }

    @Test
    void aMissingTagReachesTheServiceAsBlankAndCarriesTagBlank() {

        // given
        when(expertiseDeclarationService.declare("platform", ""))
                .thenThrow(new ValidationException("TAG_BLANK", "A tag needs a name"));

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/expertise/declarations", "platform")
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange();

        // then
        actual.assertThat().hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("TAG_BLANK");
    }

    @Test
    void theLimitIsAConflictCarryingItsCode() {

        // given
        when(expertiseDeclarationService.declare(any(), any()))
                .thenThrow(new ConflictException("DECLARATION_LIMIT", "At most 20 declared areas per community"));

        // when
        var actual = mvc.post().uri("/axonia/communities/{slug}/expertise/declarations", "platform")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tag\":\"kafka\"}").exchange();

        // then
        actual.assertThat().hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("DECLARATION_LIMIT");
    }

    @Test
    void removingReturns204() {

        // when
        var actual = mvc.delete().uri("/axonia/communities/{slug}/expertise/declarations/{tag}", "platform", "kafka")
                .exchange();

        // then
        actual.assertThat().hasStatus(204);
        verify(expertiseDeclarationService).remove("platform", "kafka");
    }
}
