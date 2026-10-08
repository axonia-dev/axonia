package dev.axonia.onboarding.internal;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.CurrentToken;
import dev.axonia.onboarding.MembershipStanding;
import dev.axonia.onboarding.OnboardingPath;
import dev.axonia.onboarding.OnboardingState;
import dev.axonia.onboarding.OnboardingStep;
import dev.axonia.onboarding.OnboardingStepKey;
import dev.axonia.onboarding.OnboardingVariant;
import dev.axonia.onboarding.OnboardingView;
import lombok.RequiredArgsConstructor;

/**
 * One person's one path. Every method authorizes on {@link CurrentToken#subject()} alone, so a token carrying no
 * {@code org} claim still reaches this surface.
 *
 * <p>
 * Assignment is best-effort and silent. A caller who is not a member, a community with nothing to point at, a path
 * already closed: all three answer with a view rather than an error, because onboarding must never be the reason a
 * shell fails to render.
 */
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final OnboardingView NONE = OnboardingView.builder().state(OnboardingState.NEVER_ASSIGNED).build();

    private static final OnboardingView CLOSED = OnboardingView.builder().state(OnboardingState.CLOSED).build();

    private static final OnboardingView COMPLETED = OnboardingView.builder().state(OnboardingState.COMPLETED).build();

    private final OnboardingPathRepository repository;

    private final OnboardingStepPlanner planner;

    private final MembershipStanding standing;

    private final Clock clock;

    @Transactional(readOnly = true)
    public OnboardingView view() {

        return repository.findById(CurrentToken.subject()).map(OnboardingService::toView).orElse(NONE);
    }

    /**
     * Idempotent by construction: an existing path is returned untouched whatever community the caller has just arrived
     * in, which is what makes "once, globally" true rather than merely intended.
     */
    @Transactional
    public OnboardingView assign(String communitySlug) {

        var subject = CurrentToken.subject();

        var existing = repository.findById(subject);
        if (existing.isPresent())
            return toView(existing.get());

        var where = standing.standingIn(communitySlug);
        if (where.isEmpty())
            return NONE;

        var variant = where.get().organizer() ? OnboardingVariant.ORGANIZER : OnboardingVariant.MEMBER;
        var steps = planner
                .plan(OnboardingStepPlanner.PlanRequest.builder().slug(communitySlug).variant(variant).build());
        if (steps.isEmpty())
            return NONE;

        return toView(repository.save(OnboardingPathEntity.builder().subject(subject).isNew(true)
                .variant(variant.name()).communitySlug(communitySlug).anchorOrganizationId(where.get().organizationId())
                .anchorCommunityId(where.get().communityId()).steps(steps).build()));
    }

    /** A visit to a step this path does not hold, or has already recorded, is a no-op rather than a refusal. */
    @Transactional
    public void visit(OnboardingStepKey key) {

        repository.findById(CurrentToken.subject()).filter(path -> path.getClosedAt() == null)
                .ifPresent(path -> record(path, key));
    }

    @Transactional
    public void close() {

        repository.findById(CurrentToken.subject()).filter(path -> path.getClosedAt() == null)
                .ifPresent(path -> repository.save(path.toBuilder().closedAt(clock.instant()).build()));
    }

    /**
     * Whether any step actually changed decides whether this writes at all - not list equality, which Lombok's
     * {@code @Builder} does not generate for {@link OnboardingStepEntity} and which would therefore compare the mapped
     * list by identity and always report a change.
     */
    private void record(OnboardingPathEntity path, OnboardingStepKey key) {

        var now = clock.instant();
        var changed = new AtomicBoolean(false);
        var updated = path.getSteps().stream().map(step -> {
            if (step.getStepKey().equals(key.name()) && step.getVisitedAt() == null) {
                changed.set(true);
                return step.withVisitedAt(now);
            }
            return step;
        }).toList();

        if (!changed.get())
            return;

        repository.save(path.toBuilder().steps(updated).closedAt(everyStepVisited(updated) ? now : null).build());
    }

    private static OnboardingView toView(OnboardingPathEntity path) {

        if (path.getClosedAt() != null)
            return everyStepVisited(path.getSteps()) ? COMPLETED : CLOSED;
        return OnboardingView.builder().state(OnboardingState.OPEN)
                .path(OnboardingPath.builder().variant(OnboardingVariant.valueOf(path.getVariant()))
                        .communitySlug(path.getCommunitySlug()).steps(toSteps(path.getSteps())).build())
                .build();
    }

    private static boolean everyStepVisited(List<OnboardingStepEntity> steps) {

        return steps.stream().allMatch(step -> step.getVisitedAt() != null);
    }

    private static List<OnboardingStep> toSteps(List<OnboardingStepEntity> steps) {

        return steps.stream()
                .map(step -> OnboardingStep.builder().key(OnboardingStepKey.valueOf(step.getStepKey()))
                        .route(step.getRoute()).detail(step.getDetail()).visited(step.getVisitedAt() != null).build())
                .toList();
    }
}
