package dev.axonia.onboarding.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.onboarding.application.port.out.OnboardingPathStore;
import dev.axonia.onboarding.domain.OnboardingPathRecord;
import dev.axonia.onboarding.domain.OnboardingStepRecord;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcOnboardingPathStore implements OnboardingPathStore {

    private final OnboardingPathRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<OnboardingPathRecord> find(String subject) {

        return repository.findById(subject).map(JdbcOnboardingPathStore::toRecord);
    }

    @Override
    @Transactional
    public OnboardingPathRecord add(OnboardingPathRecord path) {

        return toRecord(repository.save(toEntity(path, true)));
    }

    @Override
    @Transactional
    public OnboardingPathRecord update(OnboardingPathRecord path) {

        return toRecord(repository.save(toEntity(path, false)));
    }

    static OnboardingPathRecord toRecord(OnboardingPathEntity e) {

        return OnboardingPathRecord.builder().subject(e.getSubject()).variant(e.getVariant())
                .communitySlug(e.getCommunitySlug()).anchorOrganizationId(e.getAnchorOrganizationId())
                .anchorCommunityId(e.getAnchorCommunityId()).closedAt(e.getClosedAt())
                .steps(e.getSteps().stream().map(JdbcOnboardingPathStore::toRecord).toList())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt()).build();
    }

    static OnboardingPathEntity toEntity(OnboardingPathRecord path, boolean isNew) {

        return OnboardingPathEntity.builder().subject(path.getSubject()).isNew(isNew).variant(path.getVariant())
                .communitySlug(path.getCommunitySlug()).anchorOrganizationId(path.getAnchorOrganizationId())
                .anchorCommunityId(path.getAnchorCommunityId()).closedAt(path.getClosedAt())
                .steps(path.getSteps().stream().map(JdbcOnboardingPathStore::toEntity).toList())
                .createdAt(path.getCreatedAt()).updatedAt(path.getUpdatedAt()).build();
    }

    private static OnboardingStepRecord toRecord(OnboardingStepEntity step) {

        return OnboardingStepRecord.builder().stepKey(step.getStepKey()).route(step.getRoute()).detail(step.getDetail())
                .visitedAt(step.getVisitedAt()).build();
    }

    private static OnboardingStepEntity toEntity(OnboardingStepRecord step) {

        return OnboardingStepEntity.builder().stepKey(step.getStepKey()).route(step.getRoute()).detail(step.getDetail())
                .visitedAt(step.getVisitedAt()).build();
    }
}
