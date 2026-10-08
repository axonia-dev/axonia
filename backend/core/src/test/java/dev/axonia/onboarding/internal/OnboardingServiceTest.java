package dev.axonia.onboarding.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.onboarding.MembershipStanding;
import dev.axonia.onboarding.OnboardingState;
import dev.axonia.onboarding.OnboardingStepKey;
import dev.axonia.onboarding.OnboardingVariant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class OnboardingServiceTest {

    private static final String SUBJECT = "gabriel";

    @Mock
    private OnboardingPathRepository repository;

    @Mock
    private OnboardingStepPlanner planner;

    @Mock
    private MembershipStanding standing;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-12T09:00:00Z"), ZoneOffset.UTC);

    private OnboardingService service;

    @BeforeEach
    void setUp() {
        service = new OnboardingService(repository, planner, standing, clock);
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(SUBJECT).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reportsNeverAssignedForSomebodyWithNoRow() {

        when(repository.findById(SUBJECT)).thenReturn(Optional.empty());

        assertThat(service.view().state()).isEqualTo(OnboardingState.NEVER_ASSIGNED);
        assertThat(service.view().path()).isNull();
    }

    @Test
    void reportsClosedWithoutThePathOnceItIsOver() {

        when(repository.findById(SUBJECT)).thenReturn(Optional.of(closedPath()));

        assertThat(service.view().state()).isEqualTo(OnboardingState.CLOSED);
        assertThat(service.view().path()).as("a closed path is never re-rendered").isNull();
    }

    @Test
    void reportsCompletedRatherThanClosedWhenEveryStepWasSeen() {

        when(repository.findById(SUBJECT)).thenReturn(Optional.of(completedPath()));

        assertThat(service.view().state()).isEqualTo(OnboardingState.COMPLETED);
        assertThat(service.view().path()).as("a finished path is never re-rendered either").isNull();
    }

    @Test
    void assignsTheOrganizerVariantToAnOrganizer() {

        when(repository.findById(SUBJECT)).thenReturn(Optional.empty());
        when(standing.standingIn("guild")).thenReturn(Optional.of(new MembershipStanding.Standing(1L, "org-1", true)));
        when(planner.plan(any())).thenReturn(List.of(stepEntity("LOOK_AND_FEEL", "/communities/guild/look-and-feel")));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.assign("guild").path().variant()).isEqualTo(OnboardingVariant.ORGANIZER);
    }

    @Test
    void isIdempotentSoASecondArrivalNeverReplacesTheFirstPath() {

        when(repository.findById(SUBJECT)).thenReturn(Optional.of(openPath()));
        // Lenient: these three exist only to prove the idempotency check short-circuits before reaching them -
        // a correct assign() never calls any of them, so strict stubbing would flag them as unnecessary.
        lenient().when(standing.standingIn("a-different-community"))
                .thenReturn(Optional.of(new MembershipStanding.Standing(9L, "org-9", true)));
        lenient().when(planner.plan(any()))
                .thenReturn(List.of(stepEntity("LOOK_AND_FEEL", "/communities/a-different-community/look-and-feel")));
        lenient().when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        var view = service.assign("a-different-community");

        assertThat(view.path().communitySlug()).as("must stay the community the first path was assigned in")
                .isEqualTo("guild");
        assertThat(view.path().variant()).as("must stay the first path's variant, not the new community's standing")
                .isEqualTo(OnboardingVariant.MEMBER);
        verify(repository, never()).save(any());
    }

    @Test
    void refusesToAssignASecondPathAfterOneWasClosed() {

        when(repository.findById(SUBJECT)).thenReturn(Optional.of(closedPath()));

        assertThat(service.assign("guild").state()).isEqualTo(OnboardingState.CLOSED);
        verify(repository, never()).save(any());
    }

    @Test
    void assignsNothingWhenThePlannerHasNothingToPointAt() {

        when(repository.findById(SUBJECT)).thenReturn(Optional.empty());
        when(standing.standingIn("guild")).thenReturn(Optional.of(new MembershipStanding.Standing(1L, "org-1", false)));
        when(planner.plan(any())).thenReturn(List.of());

        assertThat(service.assign("guild").state()).isEqualTo(OnboardingState.NEVER_ASSIGNED);
        verify(repository, never()).save(any());
    }

    @Test
    void assignsNothingToSomebodyWhoBelongsToTheCommunityNotAtAll() {

        when(repository.findById(SUBJECT)).thenReturn(Optional.empty());
        when(standing.standingIn("guild")).thenReturn(Optional.empty());

        assertThat(service.assign("guild").state()).isEqualTo(OnboardingState.NEVER_ASSIGNED);
        verify(repository, never()).save(any());
    }

    @Test
    void closesThePathWhenTheLastUnvisitedStepIsVisited() {

        when(repository.findById(SUBJECT)).thenReturn(Optional.of(openPathWithOneUnvisitedStep()));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.visit(OnboardingStepKey.LOOK_AND_FEEL);

        var saved = ArgumentCaptor.forClass(OnboardingPathEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getClosedAt()).isNotNull();
    }

    @Test
    void keepsThePathOpenWhileAnyStepIsStillUnvisited() {

        when(repository.findById(SUBJECT)).thenReturn(Optional.of(openPath()));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.visit(OnboardingStepKey.LOOK_AND_FEEL);

        var saved = ArgumentCaptor.forClass(OnboardingPathEntity.class);
        verify(repository).save(saved.capture());
        var visitedStep = saved.getValue().getSteps().stream().filter(step -> step.getStepKey().equals("LOOK_AND_FEEL"))
                .findFirst().orElseThrow();
        assertThat(visitedStep.getVisitedAt()).isNotNull();
        assertThat(saved.getValue().getClosedAt()).isNull();
    }

    @Test
    void ignoresAVisitToAStepAlreadyVisited() {

        when(repository.findById(SUBJECT)).thenReturn(Optional.of(pathWithVisitedStep()));

        service.visit(OnboardingStepKey.LOOK_AND_FEEL);

        verify(repository, never()).save(any());
    }

    @Test
    void ignoresAVisitToAStepThisPathDoesNotHold() {

        when(repository.findById(SUBJECT)).thenReturn(Optional.of(openPath()));

        service.visit(OnboardingStepKey.TOP_TOPIC);

        verify(repository, never()).save(any());
    }

    @Test
    void closingAnOpenPathStampsClosedAtFromTheClock() {

        when(repository.findById(SUBJECT)).thenReturn(Optional.of(openPath()));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.close();

        var saved = ArgumentCaptor.forClass(OnboardingPathEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getClosedAt()).isEqualTo(clock.instant());
    }

    @Test
    void closingAnAlreadyClosedPathWritesNothing() {

        when(repository.findById(SUBJECT)).thenReturn(Optional.of(closedPath()));

        service.close();

        verify(repository, never()).save(any());
    }

    @Test
    void closingWithNoPathAtAllWritesNothing() {

        when(repository.findById(SUBJECT)).thenReturn(Optional.empty());

        service.close();

        verify(repository, never()).save(any());
    }

    private static OnboardingPathEntity openPath() {

        return OnboardingPathEntity.builder().subject(SUBJECT).variant("MEMBER").communitySlug("guild")
                .anchorOrganizationId("org-1").anchorCommunityId(1L)
                .steps(List.of(stepEntity("LOOK_AND_FEEL", "/communities/guild/look-and-feel"),
                        stepEntity("FIRST_SESSION", "/communities/guild/schedule")))
                .build();
    }

    private static OnboardingPathEntity closedPath() {

        return openPath().toBuilder().closedAt(Instant.parse("2026-09-01T00:00:00Z")).build();
    }

    private static OnboardingPathEntity completedPath() {

        var seen = Instant.parse("2026-09-01T00:00:00Z");
        return closedPath().toBuilder()
                .steps(closedPath().getSteps().stream().map(step -> step.withVisitedAt(seen)).toList()).build();
    }

    private static OnboardingPathEntity openPathWithOneUnvisitedStep() {

        return OnboardingPathEntity.builder().subject(SUBJECT).variant("MEMBER").communitySlug("guild")
                .anchorOrganizationId("org-1").anchorCommunityId(1L)
                .steps(List.of(stepEntity("LOOK_AND_FEEL", "/communities/guild/look-and-feel"))).build();
    }

    private static OnboardingPathEntity pathWithVisitedStep() {

        return OnboardingPathEntity.builder().subject(SUBJECT).variant("MEMBER").communitySlug("guild")
                .anchorOrganizationId("org-1").anchorCommunityId(1L)
                .steps(List.of(stepEntity("LOOK_AND_FEEL", "/communities/guild/look-and-feel")
                        .withVisitedAt(Instant.parse("2026-09-01T00:00:00Z"))))
                .build();
    }

    private static OnboardingStepEntity stepEntity(String key, String route) {

        return OnboardingStepEntity.builder().stepKey(key).route(route).build();
    }
}
