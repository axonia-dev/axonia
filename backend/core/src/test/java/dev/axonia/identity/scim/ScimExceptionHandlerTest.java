package dev.axonia.identity.scim;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;

@CoreWebMvcTest(controllers = ScimExceptionHandlerTest.StubController.class)
@Import({ScimExceptionHandler.class, ScimExceptionHandlerTest.StubController.class})
class ScimExceptionHandlerTest extends ControllerTestSupport {

    @RestController
    static class StubController {

        record ValidatedInput(@NotNull String field) {
        }

        @GetMapping("/scim/v2/stub/not-found")
        void notFound() {

            throw ScimException.notFound("resource missing");
        }

        @GetMapping("/scim/v2/stub/conflict")
        void conflict() {

            throw ScimException.uniquenessConflict("resource already exists");
        }

        @GetMapping("/scim/v2/stub/bad-request")
        void badRequest() {

            throw ScimException.badRequest("invalid input");
        }

        @GetMapping("/scim/v2/stub/unauthorized")
        void unauthorized() {

            throw ScimException.unauthorized("token invalid");
        }

        @PostMapping("/scim/v2/stub/validated")
        void validated(@Valid @RequestBody ValidatedInput body) {
        }
    }

    @Test
    void handlesNotFound() {

        // when
        var actual = mvc.get().uri("/scim/v2/stub/not-found");

        // then
        actual.assertThat().hasStatus(404)
                .hasHeader("Content-Type", MediaType.valueOf("application/scim+json").toString()).bodyJson()
                .extractingPath("$.schemas[0]").isEqualTo("urn:ietf:params:scim:api:messages:2.0:Error");
        actual.assertThat().bodyJson().extractingPath("$.status").isEqualTo("404");
        actual.assertThat().bodyJson().extractingPath("$.detail").isEqualTo("resource missing");
    }

    @Test
    void handlesConflict() {

        // when
        var actual = mvc.get().uri("/scim/v2/stub/conflict");

        // then
        actual.assertThat().hasStatus(409).bodyJson().extractingPath("$.status").isEqualTo("409");
    }

    @Test
    void handlesConflict_serialisesScimTypeUniqueness() {

        // when
        var actual = mvc.get().uri("/scim/v2/stub/conflict");

        // then - the raw JSON must carry the key, not just a null field, so Entra can key its recovery on it
        actual.assertThat().bodyText().contains("\"scimType\":\"uniqueness\"");
    }

    @Test
    void handlesNotFound_omitsScimTypeEntirely() {

        // when
        var actual = mvc.get().uri("/scim/v2/stub/not-found");

        // then - a null scimType must be absent from the wire shape, not serialised as "scimType":null
        actual.assertThat().bodyText().doesNotContain("scimType");
    }

    @Test
    void handlesBadRequest() {

        // when
        var actual = mvc.get().uri("/scim/v2/stub/bad-request");

        // then
        actual.assertThat().hasStatus(400).bodyJson().extractingPath("$.status").isEqualTo("400");
    }

    @Test
    void handlesUnauthorized() {

        // when
        var actual = mvc.get().uri("/scim/v2/stub/unauthorized");

        // then
        actual.assertThat().hasStatus(401).bodyJson().extractingPath("$.status").isEqualTo("401");
    }

    @Test
    void handlesValidation_returns400ScimError() {

        // when — send null for the @NotNull field to trigger MethodArgumentNotValidException
        var actual = mvc.post().uri("/scim/v2/stub/validated").contentType(MediaType.APPLICATION_JSON)
                .content("{\"field\":null}");

        // then
        actual.assertThat().hasStatus(400)
                .hasHeader("Content-Type", MediaType.valueOf("application/scim+json").toString()).bodyJson()
                .extractingPath("$.status").isEqualTo("400");
    }
}
