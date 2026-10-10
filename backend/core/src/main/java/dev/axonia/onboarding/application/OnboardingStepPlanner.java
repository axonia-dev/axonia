package dev.axonia.onboarding.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import dev.axonia.onboarding.application.port.out.CommunityAgenda;
import dev.axonia.onboarding.application.port.out.LovedArtifacts;
import dev.axonia.onboarding.domain.OnboardingStepKey;
import dev.axonia.onboarding.domain.OnboardingStepRecord;
import dev.axonia.onboarding.domain.OnboardingVariant;
import dev.axonia.onboarding.domain.Pick;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

/**
 * Turns a variant and a community into the frozen list of steps a path will carry for the rest of its life.
 *
 * <p>
 * The organizer path is fixed: its six steps name places, every one of which exists for any community, and an organizer
 * opens every management axis so all six are reachable.
 *
 * <p>
 * The member path is not. Four of its five steps name a thing rather than a place, so each is dropped when the
 * community has no such thing, and a community with none of them is given no path at all - a path reading only "say
 * what you know" is worse than silence.
 */
@Component
@RequiredArgsConstructor
public class OnboardingStepPlanner {

    private final LovedArtifacts loved;

    private final CommunityAgenda agenda;

    @Builder
    public record PlanRequest(String slug, OnboardingVariant variant) {
    }

    /** An empty list means: assign no path. */
    public List<OnboardingStepRecord> plan(PlanRequest request) {

        return request.variant() == OnboardingVariant.ORGANIZER
                ? organizerSteps(request.slug())
                : memberSteps(request.slug());
    }

    private static List<OnboardingStepRecord> organizerSteps(String slug) {

        var base = "/communities/" + slug;
        return List.of(step(OnboardingStepKey.LOOK_AND_FEEL, base + "/look-and-feel", null),
                step(OnboardingStepKey.FIRST_SESSION, base + "/schedule", null),
                step(OnboardingStepKey.BACKLOG, base + "/backlog", null),
                step(OnboardingStepKey.FIRST_PEOPLE, base + "/settings/members", null),
                step(OnboardingStepKey.LIBRARY, base + "/library", null),
                step(OnboardingStepKey.HEALTH, base + "/health", null));
    }

    private List<OnboardingStepRecord> memberSteps(String slug) {

        var base = "/communities/" + slug;
        var steps = new ArrayList<OnboardingStepRecord>();
        add(steps, loved.mostLoved(slug), OnboardingStepKey.MOST_LOVED_ARTIFACT,
                pick -> base + "/library?focus=" + pick.id());
        add(steps, agenda.lastHeld(slug), OnboardingStepKey.LAST_HELD_SESSION, pick -> base + "/sessions/" + pick.id());
        add(steps, agenda.nextUp(slug), OnboardingStepKey.NEXT_SESSION, pick -> base + "/sessions/" + pick.id());
        add(steps, agenda.topVotedTopic(slug), OnboardingStepKey.TOP_TOPIC, pick -> base + "/backlog");
        if (steps.isEmpty())
            return List.of();
        steps.add(step(OnboardingStepKey.DECLARE_EXPERTISE, base + "/me/expertise", null));
        return List.copyOf(steps);
    }

    private static void add(List<OnboardingStepRecord> steps, Optional<Pick> pick, OnboardingStepKey key,
            Function<Pick, String> route) {

        pick.ifPresent(found -> steps.add(step(key, route.apply(found), found.label())));
    }

    private static OnboardingStepRecord step(OnboardingStepKey key, String route, String detail) {

        return OnboardingStepRecord.builder().stepKey(key.name()).route(route).detail(detail).build();
    }
}
