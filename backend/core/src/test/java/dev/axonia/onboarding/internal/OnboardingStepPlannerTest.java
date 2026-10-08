package dev.axonia.onboarding.internal;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.onboarding.CommunityAgenda;
import dev.axonia.onboarding.LovedArtifacts;
import dev.axonia.onboarding.OnboardingVariant;
import dev.axonia.onboarding.Pick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class OnboardingStepPlannerTest {

    @Mock
    private LovedArtifacts loved;

    @Mock
    private CommunityAgenda agenda;

    @InjectMocks
    private OnboardingStepPlanner planner;

    @Test
    void givesAnOrganizerTheSameSixStepsWhateverTheCommunityHolds() {

        var steps = planner.plan(request("guild", OnboardingVariant.ORGANIZER));

        assertThat(steps).extracting(OnboardingStepEntity::getStepKey).containsExactly("LOOK_AND_FEEL", "FIRST_SESSION",
                "BACKLOG", "FIRST_PEOPLE", "LIBRARY", "HEALTH");
        assertThat(steps).extracting(OnboardingStepEntity::getRoute).containsExactly("/communities/guild/look-and-feel",
                "/communities/guild/schedule", "/communities/guild/backlog", "/communities/guild/settings/members",
                "/communities/guild/library", "/communities/guild/health");
    }

    @Test
    void freezesEachMemberStepOntoTheThingItPointedAt() {

        when(loved.mostLoved("guild")).thenReturn(Optional.of(new Pick(7L, "The deploy checklist")));
        when(agenda.lastHeld("guild")).thenReturn(Optional.of(new Pick(3L, "2026-09-04")));
        when(agenda.nextUp("guild")).thenReturn(Optional.of(new Pick(4L, "2026-09-16")));
        when(agenda.topVotedTopic("guild")).thenReturn(Optional.of(new Pick(5L, "one build cache")));

        var steps = planner.plan(request("guild", OnboardingVariant.MEMBER));

        assertThat(steps).extracting(OnboardingStepEntity::getStepKey).containsExactly("MOST_LOVED_ARTIFACT",
                "LAST_HELD_SESSION", "NEXT_SESSION", "TOP_TOPIC", "DECLARE_EXPERTISE");
        assertThat(steps).extracting(OnboardingStepEntity::getRoute).containsExactly(
                "/communities/guild/library?focus=7", "/communities/guild/sessions/3", "/communities/guild/sessions/4",
                "/communities/guild/backlog", "/communities/guild/me/expertise");
        assertThat(steps.get(0).getDetail()).isEqualTo("The deploy checklist");
    }

    @Test
    void givesTheTwoSessionStepsDistinctRoutesSoOneVisitCannotTickBoth() {

        when(agenda.lastHeld("guild")).thenReturn(Optional.of(new Pick(3L, "2026-09-04")));
        when(agenda.nextUp("guild")).thenReturn(Optional.of(new Pick(4L, "2026-09-16")));

        var routes = planner.plan(request("guild", OnboardingVariant.MEMBER)).stream()
                .map(OnboardingStepEntity::getRoute).toList();

        assertThat(routes).doesNotHaveDuplicates();
    }

    @Test
    void dropsAMemberStepThatHasNothingToPointAt() {

        when(loved.mostLoved("guild")).thenReturn(Optional.empty());
        when(agenda.lastHeld("guild")).thenReturn(Optional.empty());
        when(agenda.nextUp("guild")).thenReturn(Optional.of(new Pick(4L, "2026-09-16")));
        when(agenda.topVotedTopic("guild")).thenReturn(Optional.empty());

        var steps = planner.plan(request("guild", OnboardingVariant.MEMBER));

        assertThat(steps).extracting(OnboardingStepEntity::getStepKey).containsExactly("NEXT_SESSION",
                "DECLARE_EXPERTISE");
    }

    @Test
    void plansNothingForAMemberOfACommunityWithNothingToShow() {

        when(loved.mostLoved("guild")).thenReturn(Optional.empty());
        when(agenda.lastHeld("guild")).thenReturn(Optional.empty());
        when(agenda.nextUp("guild")).thenReturn(Optional.empty());
        when(agenda.topVotedTopic("guild")).thenReturn(Optional.empty());

        assertThat(planner.plan(request("guild", OnboardingVariant.MEMBER)))
                .as("a one-step path reading only 'say what you know' is worse than no path at all").isEmpty();
    }

    private static OnboardingStepPlanner.PlanRequest request(String slug, OnboardingVariant variant) {

        return OnboardingStepPlanner.PlanRequest.builder().slug(slug).variant(variant).build();
    }
}
