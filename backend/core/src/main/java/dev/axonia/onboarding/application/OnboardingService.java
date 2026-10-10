package dev.axonia.onboarding.application;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.onboarding.application.port.out.MembershipStanding;
import dev.axonia.onboarding.application.port.out.OnboardingPathStore;
import dev.axonia.onboarding.domain.OnboardingPath;
import dev.axonia.onboarding.domain.OnboardingPathRecord;
import dev.axonia.onboarding.domain.OnboardingState;
import dev.axonia.onboarding.domain.OnboardingStep;
import dev.axonia.onboarding.domain.OnboardingStepKey;
import dev.axonia.onboarding.domain.OnboardingStepRecord;
import dev.axonia.onboarding.domain.OnboardingVariant;
import dev.axonia.shared.api.security.CurrentToken;
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

    private final OnboardingPathStore store;

    private final OnboardingStepPlanner planner;

    private final MembershipStanding standing;

    private final Clock clock;

    @Transactional(readOnly = true)
    public OnboardingView view() {

        return store.find(CurrentToken.subject()).map(OnboardingService::toView).orElse(NONE);
    }

    /**
     * Idempotent by construction: an existing path is returned untouched whatever community the caller has just arrived
     * in, which is what makes "once, globally" true rather than merely intended.
     */
    @Transactional
    public OnboardingView assign(String communitySlug) {

        var subject = CurrentToken.subject();

        var existing = store.find(subject);
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

        return toView(store.add(OnboardingPathRecord.builder().subject(subject).variant(variant.name())
                .communitySlug(communitySlug).anchorOrganizationId(where.get().organizationId())
                .anchorCommunityId(where.get().communityId()).steps(steps).build()));
    }

    /** A visit to a step this path does not hold, or has already recorded, is a no-op rather than a refusal. */
    @Transactional
    public void visit(OnboardingStepKey key) {

        store.find(CurrentToken.subject()).filter(path -> path.getClosedAt() == null)
                .ifPresent(path -> record(path, key));
    }

    @Transactional
    public void close() {

        store.find(CurrentToken.subject()).filter(path -> path.getClosedAt() == null)
                .ifPresent(path -> store.update(path.toBuilder().closedAt(clock.instant()).build()));
    }

    /**
     * Whether any step actually changed decides whether this writes at all, tracked as the steps are mapped rather than
     * recovered afterwards by comparing the lists.
     */
    private void record(OnboardingPathRecord path, OnboardingStepKey key) {

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

        store.update(path.toBuilder().steps(updated).closedAt(everyStepVisited(updated) ? now : null).build());
    }

    private static OnboardingView toView(OnboardingPathRecord path) {

        if (path.getClosedAt() != null)
            return everyStepVisited(path.getSteps()) ? COMPLETED : CLOSED;
        return OnboardingView.builder().state(OnboardingState.OPEN)
                .path(OnboardingPath.builder().variant(OnboardingVariant.valueOf(path.getVariant()))
                        .communitySlug(path.getCommunitySlug()).steps(toSteps(path.getSteps())).build())
                .build();
    }

    private static boolean everyStepVisited(List<OnboardingStepRecord> steps) {

        return steps.stream().allMatch(step -> step.getVisitedAt() != null);
    }

    private static List<OnboardingStep> toSteps(List<OnboardingStepRecord> steps) {

        return steps.stream()
                .map(step -> OnboardingStep.builder().key(OnboardingStepKey.valueOf(step.getStepKey()))
                        .route(step.getRoute()).detail(step.getDetail()).visited(step.getVisitedAt() != null).build())
                .toList();
    }
}
