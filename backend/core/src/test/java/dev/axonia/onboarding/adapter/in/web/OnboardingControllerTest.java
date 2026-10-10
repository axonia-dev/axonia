package dev.axonia.onboarding.adapter.in.web;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.onboarding.application.OnboardingService;
import dev.axonia.onboarding.application.OnboardingView;
import dev.axonia.onboarding.domain.OnboardingPath;
import dev.axonia.onboarding.domain.OnboardingState;
import dev.axonia.onboarding.domain.OnboardingStep;
import dev.axonia.onboarding.domain.OnboardingStepKey;
import dev.axonia.onboarding.domain.OnboardingVariant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A {@code @WebMvcTest} slice for {@link OnboardingController}, organization-agnostic exactly as
 * {@code StandingControllerTest} is: every call authorizes on the default JWT's subject alone.
 */
@CoreWebMvcTest(controllers = OnboardingController.class)
class OnboardingControllerTest extends ControllerTestSupport {

    @MockitoBean
    private OnboardingService onboarding;

    @Test
    void answersNeverAssignedAsAStateRatherThanAnEmptyBody() {

        when(onboarding.view()).thenReturn(OnboardingView.builder().state(OnboardingState.NEVER_ASSIGNED).build());

        assertThat(mvc.get().uri(path("/me/onboarding"))).hasStatusOk().bodyJson().extractingPath("$.state")
                .isEqualTo("NEVER_ASSIGNED");
    }

    @Test
    void assigningAPathReturnsTheResultingView() {

        when(onboarding.assign("acme")).thenReturn(OnboardingView.builder().state(OnboardingState.OPEN)
                .path(OnboardingPath.builder().variant(OnboardingVariant.MEMBER).communitySlug("acme").build())
                .build());

        var actual = mvc.post().uri(path("/me/onboarding")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"communitySlug\":\"acme\"}").exchange();

        actual.assertThat().hasStatusOk().bodyJson().extractingPath("$.path.communitySlug").isEqualTo("acme");
        verify(onboarding).assign("acme");
    }

    @Test
    void answersAnOpenPathWithItsStepsThroughGet() {

        when(onboarding.view())
                .thenReturn(OnboardingView.builder().state(OnboardingState.OPEN).path(OnboardingPath.builder()
                        .variant(OnboardingVariant.ORGANIZER).communitySlug("acme").steps(List.of(OnboardingStep
                                .builder().key(OnboardingStepKey.BACKLOG).route("/backlog").visited(true).build()))
                        .build()).build());

        var actual = mvc.get().uri(path("/me/onboarding")).exchange();

        actual.assertThat().hasStatusOk().bodyJson().extractingPath("$.path.variant").isEqualTo("ORGANIZER");
        actual.assertThat().bodyJson().extractingPath("$.path.communitySlug").isEqualTo("acme");
        actual.assertThat().bodyJson().extractingPath("$.path.steps[0].route").isEqualTo("/backlog");
        actual.assertThat().bodyJson().extractingPath("$.path.steps[0].visited").isEqualTo(true);
    }

    @Test
    void refusesAnAssignmentWithNoCommunity() {

        assertThat(mvc.post().uri(path("/me/onboarding")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"communitySlug\":\"\"}")).hasStatus(HttpStatus.BAD_REQUEST);
        verify(onboarding, never()).assign(any());
    }

    @Test
    void aVisitWritesThroughTheService() {

        var actual = mvc.post().uri(path("/me/onboarding/visits")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stepKey\":\"BACKLOG\"}").exchange();

        actual.assertThat().hasStatus(HttpStatus.NO_CONTENT);
        verify(onboarding).visit(OnboardingStepKey.BACKLOG);
    }

    @Test
    void refusesAVisitNamingAStepKeyThatDoesNotExist() {

        assertThat(mvc.post().uri(path("/me/onboarding/visits")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stepKey\":\"NOT_A_STEP\"}")).hasStatus(HttpStatus.BAD_REQUEST);
        verify(onboarding, never()).visit(any());
    }

    @Test
    void refusesAVisitWithNoStepKey() {

        assertThat(mvc.post().uri(path("/me/onboarding/visits")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stepKey\":null}")).hasStatus(HttpStatus.BAD_REQUEST);
        verify(onboarding, never()).visit(any());
    }

    @Test
    void closingWritesThroughTheService() {

        assertThat(mvc.post().uri(path("/me/onboarding/close"))).hasStatus(HttpStatus.NO_CONTENT);

        verify(onboarding).close();
    }

    private static String path(String suffix) {

        return "/axonia" + suffix;
    }
}
