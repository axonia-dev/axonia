package dev.axonia.shared.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.MissingOrganizationException;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;

@CoreWebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest extends ControllerTestSupport {

    record Body(@NotBlank String name) {
    }

    @RestController
    static class TestController {

        @PostMapping("/test/body-valid")
        void bodyValid(@Valid @RequestBody Body body) {
        }

        @GetMapping("/test/not-found")
        void notFound() {

            throw new NotFoundException("missing");
        }

        @GetMapping("/test/conflict")
        void conflict() {

            throw new ConflictException("ALREADY_MEMBER", "already a member");
        }

        @GetMapping("/test/forbidden")
        void forbidden() {

            throw new ForbiddenException("organizer required");
        }

        @GetMapping("/test/missing-organization")
        void missingOrganization() {

            throw new MissingOrganizationException("sub-123");
        }
    }

    /**
     * The regression this guards: Spring Boot auto-configures its own unordered {@code ProblemDetailsExceptionHandler}
     * that already handles {@code MethodArgumentNotValidException}, and it silently wins the tie over an unordered
     * {@link GlobalExceptionHandler} - proven by writing this test before {@code @Order} was added and watching it fail
     * with a bare, codeless body even though {@link #handleFieldValidation} compiled and was wired.
     */
    @Test
    void handlesFieldValidation() {

        // when
        var actual = mvc.post().uri("/test/body-valid").contentType(MediaType.APPLICATION_JSON).content("{}");

        // then
        actual.assertThat().hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("field_invalid");
        actual.assertThat().bodyJson().extractingPath("$.field").isEqualTo("name");
    }

    /**
     * A {@link MethodArgumentNotValidException} does not always carry a field error: a class-level constraint (a
     * cross-field {@code @ScriptAssert}, a custom type-level {@code @Constraint}) reports through
     * {@code BindingResult#getGlobalErrors()} instead, so {@code getFieldErrors()} comes back empty. Built by hand here
     * rather than through a real class-level annotation, because the exact shape under test - zero field errors, one
     * global error - is what matters, not which annotation produced it. No field name means no {@code field} property
     * and the fixed fallback detail, never a {@code null: ...} prefix.
     */
    @Test
    void handlesAFailureCarryingOnlyGlobalErrors() throws NoSuchMethodException {

        // given
        var bindingResult = new BeanPropertyBindingResult(new Body("ignored"), "body");
        bindingResult.addError(new ObjectError("body", "must satisfy a rule spanning more than one field"));
        var parameter = new MethodParameter(TestController.class.getDeclaredMethod("bodyValid", Body.class), 0);
        var ex = new MethodArgumentNotValidException(parameter, bindingResult);

        // when
        var problem = new GlobalExceptionHandler().handleFieldValidation(ex);

        // then
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("The request body failed validation.");
        assertThat(problem.getProperties()).containsEntry("code", "field_invalid").doesNotContainKey("field");
    }

    @Test
    void handlesNotFound() {

        // when
        var actual = mvc.get().uri("/test/not-found");

        // then
        actual.assertThat().hasStatus(404).bodyJson().extractingPath("$.detail").isEqualTo("missing");
    }

    @Test
    void handlesConflict() {

        // when
        var actual = mvc.get().uri("/test/conflict");

        // then
        actual.assertThat().hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("ALREADY_MEMBER");
    }

    @Test
    void handlesForbidden() {

        // when
        var actual = mvc.get().uri("/test/forbidden");

        // then
        actual.assertThat().hasStatus(403);
    }

    @Test
    void handlesMissingTenant() {

        // when
        var actual = mvc.get().uri("/test/missing-organization");

        // then
        actual.assertThat().hasStatus(401);
    }
}
