package dev.axonia.onboarding.domain;

import java.time.Instant;
import java.util.List;

import lombok.Builder;
import lombok.Value;

/** A person's path as it is stored: every column, so a write round-trips exactly. */
@Value
@Builder(toBuilder = true)
public class OnboardingPathRecord {

    String subject;

    String variant;

    String communitySlug;

    String anchorOrganizationId;

    Long anchorCommunityId;

    Instant closedAt;

    List<OnboardingStepRecord> steps;

    Instant createdAt;

    Instant updatedAt;
}
