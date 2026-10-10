package dev.axonia.onboarding.application;

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

import dev.axonia.onboarding.application.port.out.MembershipStanding;
import dev.axonia.onboarding.application.port.out.OnboardingPathStore;
import dev.axonia.onboarding.domain.OnboardingPathRecord;
import dev.axonia.onboarding.domain.OnboardingState;
import dev.axonia.onboarding.domain.OnboardingStepKey;
import dev.axonia.onboarding.domain.OnboardingStepRecord;
import dev.axonia.onboarding.domain.OnboardingVariant;

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
    private OnboardingPathStore store;

    @Mock
    private OnboardingStepPlanner planner;

    @Mock
    private MembershipStanding standing;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-12T09:00:00Z"), ZoneOffset.UTC);

    private OnboardingService service;

    @BeforeEach
    void setUp() {
        service = new OnboardingService(store, planner, standing, clock);
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(SUBJECT).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reportsNeverAssignedForSomebodyWithNoRow() {

        when(store.find(SUBJECT)).thenReturn(Optional.empty());

        assertThat(service.view().state()).isEqualTo(OnboardingState.NEVER_ASSIGNED);
        assertThat(service.view().path()).isNull();
    }

    @Test
    void reportsClosedWithoutThePathOnceItIsOver() {

        when(store.find(SUBJECT)).thenReturn(Optional.of(closedPath()));

        assertThat(service.view().state()).isEqualTo(OnboardingState.CLOSED);
        assertThat(service.view().path()).as("a closed path is never re-rendered").isNull();
    }

    @Test
    void reportsCompletedRatherThanClosedWhenEveryStepWasSeen() {

        when(store.find(SUBJECT)).thenReturn(Optional.of(completedPath()));

        assertThat(service.view().state()).isEqualTo(OnboardingState.COMPLETED);
        assertThat(service.view().path()).as("a finished path is never re-rendered either").isNull();
    }

    @Test
    void assignsTheOrganizerVariantToAnOrganizer() {

        when(store.find(SUBJECT)).thenReturn(Optional.empty());
        when(standing.standingIn("guild")).thenReturn(Optional.of(new MembershipStanding.Standing(1L, "org-1", true)));
        when(planner.plan(any())).thenReturn(List.of(stepRecord("LOOK_AND_FEEL", "/communities/guild/look-and-feel")));
        when(store.add(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.assign("guild").path().variant()).isEqualTo(OnboardingVariant.ORGANIZER);
    }

    @Test
    void isIdempotentSoASecondArrivalNeverReplacesTheFirstPath() {

        when(store.find(SUBJECT)).thenReturn(Optional.of(openPath()));
        // Lenient: these three exist only to prove the idempotency check short-circuits before reaching them -
        // a correct assign() never calls any of them, so strict stubbing would flag them as unnecessary.
        lenient().when(standing.standingIn("a-different-community"))
                .thenReturn(Optional.of(new MembershipStanding.Standing(9L, "org-9", true)));
        lenient().when(planner.plan(any()))
                .thenReturn(List.of(stepRecord("LOOK_AND_FEEL", "/communities/a-different-community/look-and-feel")));
        lenient().when(store.add(any())).thenAnswer(call -> call.getArgument(0));

        var view = service.assign("a-different-community");

        assertThat(view.path().communitySlug()).as("must stay the community the first path was assigned in")
                .isEqualTo("guild");
        assertThat(view.path().variant()).as("must stay the first path's variant, not the new community's standing")
                .isEqualTo(OnboardingVariant.MEMBER);
        verify(store, never()).add(any());
        verify(store, never()).update(any());
    }

    @Test
    void refusesToAssignASecondPathAfterOneWasClosed() {

        when(store.find(SUBJECT)).thenReturn(Optional.of(closedPath()));

        assertThat(service.assign("guild").state()).isEqualTo(OnboardingState.CLOSED);
        verify(store, never()).add(any());
        verify(store, never()).update(any());
    }

    @Test
    void assignsNothingWhenThePlannerHasNothingToPointAt() {

        when(store.find(SUBJECT)).thenReturn(Optional.empty());
        when(standing.standingIn("guild")).thenReturn(Optional.of(new MembershipStanding.Standing(1L, "org-1", false)));
        when(planner.plan(any())).thenReturn(List.of());

        assertThat(service.assign("guild").state()).isEqualTo(OnboardingState.NEVER_ASSIGNED);
        verify(store, never()).add(any());
        verify(store, never()).update(any());
    }

    @Test
    void assignsNothingToSomebodyWhoBelongsToTheCommunityNotAtAll() {

        when(store.find(SUBJECT)).thenReturn(Optional.empty());
        when(standing.standingIn("guild")).thenReturn(Optional.empty());

        assertThat(service.assign("guild").state()).isEqualTo(OnboardingState.NEVER_ASSIGNED);
        verify(store, never()).add(any());
        verify(store, never()).update(any());
    }

    @Test
    void closesThePathWhenTheLastUnvisitedStepIsVisited() {

        when(store.find(SUBJECT)).thenReturn(Optional.of(openPathWithOneUnvisitedStep()));
        when(store.update(any())).thenAnswer(call -> call.getArgument(0));

        service.visit(OnboardingStepKey.LOOK_AND_FEEL);

        var saved = ArgumentCaptor.forClass(OnboardingPathRecord.class);
        verify(store).update(saved.capture());
        assertThat(saved.getValue().getClosedAt()).isNotNull();
    }

    @Test
    void keepsThePathOpenWhileAnyStepIsStillUnvisited() {

        when(store.find(SUBJECT)).thenReturn(Optional.of(openPath()));
        when(store.update(any())).thenAnswer(call -> call.getArgument(0));

        service.visit(OnboardingStepKey.LOOK_AND_FEEL);

        var saved = ArgumentCaptor.forClass(OnboardingPathRecord.class);
        verify(store).update(saved.capture());
        var visitedStep = saved.getValue().getSteps().stream().filter(step -> step.getStepKey().equals("LOOK_AND_FEEL"))
                .findFirst().orElseThrow();
        assertThat(visitedStep.getVisitedAt()).isNotNull();
        assertThat(saved.getValue().getClosedAt()).isNull();
    }

    @Test
    void ignoresAVisitToAStepAlreadyVisited() {

        when(store.find(SUBJECT)).thenReturn(Optional.of(pathWithVisitedStep()));

        service.visit(OnboardingStepKey.LOOK_AND_FEEL);

        verify(store, never()).add(any());
        verify(store, never()).update(any());
    }

    @Test
    void ignoresAVisitToAStepThisPathDoesNotHold() {

        when(store.find(SUBJECT)).thenReturn(Optional.of(openPath()));

        service.visit(OnboardingStepKey.TOP_TOPIC);

        verify(store, never()).add(any());
        verify(store, never()).update(any());
    }

    @Test
    void closingAnOpenPathStampsClosedAtFromTheClock() {

        when(store.find(SUBJECT)).thenReturn(Optional.of(openPath()));
        when(store.update(any())).thenAnswer(call -> call.getArgument(0));

        service.close();

        var saved = ArgumentCaptor.forClass(OnboardingPathRecord.class);
        verify(store).update(saved.capture());
        assertThat(saved.getValue().getClosedAt()).isEqualTo(clock.instant());
    }

    @Test
    void closingAnAlreadyClosedPathWritesNothing() {

        when(store.find(SUBJECT)).thenReturn(Optional.of(closedPath()));

        service.close();

        verify(store, never()).add(any());
        verify(store, never()).update(any());
    }

    @Test
    void closingWithNoPathAtAllWritesNothing() {

        when(store.find(SUBJECT)).thenReturn(Optional.empty());

        service.close();

        verify(store, never()).add(any());
        verify(store, never()).update(any());
    }

    private static OnboardingPathRecord openPath() {

        return OnboardingPathRecord.builder().subject(SUBJECT).variant("MEMBER").communitySlug("guild")
                .anchorOrganizationId("org-1").anchorCommunityId(1L)
                .steps(List.of(stepRecord("LOOK_AND_FEEL", "/communities/guild/look-and-feel"),
                        stepRecord("FIRST_SESSION", "/communities/guild/schedule")))
                .build();
    }

    private static OnboardingPathRecord closedPath() {

        return openPath().toBuilder().closedAt(Instant.parse("2026-09-01T00:00:00Z")).build();
    }

    private static OnboardingPathRecord completedPath() {

        var seen = Instant.parse("2026-09-01T00:00:00Z");
        return closedPath().toBuilder()
                .steps(closedPath().getSteps().stream().map(step -> step.withVisitedAt(seen)).toList()).build();
    }

    private static OnboardingPathRecord openPathWithOneUnvisitedStep() {

        return OnboardingPathRecord.builder().subject(SUBJECT).variant("MEMBER").communitySlug("guild")
                .anchorOrganizationId("org-1").anchorCommunityId(1L)
                .steps(List.of(stepRecord("LOOK_AND_FEEL", "/communities/guild/look-and-feel"))).build();
    }

    private static OnboardingPathRecord pathWithVisitedStep() {

        return OnboardingPathRecord.builder().subject(SUBJECT).variant("MEMBER").communitySlug("guild")
                .anchorOrganizationId("org-1").anchorCommunityId(1L)
                .steps(List.of(stepRecord("LOOK_AND_FEEL", "/communities/guild/look-and-feel")
                        .withVisitedAt(Instant.parse("2026-09-01T00:00:00Z"))))
                .build();
    }

    private static OnboardingStepRecord stepRecord(String key, String route) {

        return OnboardingStepRecord.builder().stepKey(key).route(route).build();
    }
}
