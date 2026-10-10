package dev.axonia.onboarding.application.port.out;

import java.util.Optional;

import dev.axonia.onboarding.domain.OnboardingPathRecord;

/** One path per subject; every read is already narrowed to one person by the database. */
public interface OnboardingPathStore {

    Optional<OnboardingPathRecord> find(String subject);

    /** Stores a path the subject did not have. */
    OnboardingPathRecord add(OnboardingPathRecord path);

    OnboardingPathRecord update(OnboardingPathRecord path);
}
