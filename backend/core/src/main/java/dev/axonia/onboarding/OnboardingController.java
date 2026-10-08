package dev.axonia.onboarding;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.CurrentToken;
import dev.axonia.onboarding.internal.OnboardingService;
import lombok.RequiredArgsConstructor;

/**
 * The caller's own onboarding path: organization-agnostic, exactly as {@code GET /me/standing} already is. A path
 * belongs to a person and travels with them, so every method authorizes on {@link CurrentToken#subject()} alone.
 *
 * <p>
 * An edition that offers delegated sessions refuses these three writes to one, since each is a write on the person's
 * own profile.
 */
@RestController
@Validated
@RequestMapping("/${spring.application.name}/me/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboarding;

    @GetMapping
    OnboardingView view() {

        return onboarding.view();
    }

    /** Idempotent: a caller who already has a path gets it back rather than a second one. */
    @PostMapping
    OnboardingView assign(@RequestBody @Valid Assignment request) {

        return onboarding.assign(request.communitySlug());
    }

    @PostMapping("/visits")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void visit(@RequestBody @Valid Visit request) {

        onboarding.visit(request.stepKey());
    }

    @PostMapping("/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void close() {

        onboarding.close();
    }

    record Assignment(@NotBlank String communitySlug) {
    }

    record Visit(@NotNull OnboardingStepKey stepKey) {
    }
}
