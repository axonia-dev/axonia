package dev.axonia.onboarding.adapter.out.persistence;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.JdbcTestSupport;
import dev.axonia.shared.api.tenancy.SubjectScope;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs as the Testcontainers table owner, which bypasses {@code onboarding_path}'s row-level security by ownership -
 * the convention every {@code JdbcTestSupport} integration test here follows. The isolation policy itself is proved
 * against a non-owner connection in {@code RlsIsolationTest}; what this class proves is the round trip: the ordered
 * child collection saves and reads back in the order the planner chose.
 */
class OnboardingPathRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    private OnboardingPathRepository repository;

    @Test
    void roundTripsTheStepsInTheOrderThePlannerChose() {

        SubjectScope.runAs("person-a", () -> repository.save(pathFor("person-a")));

        var found = SubjectScope.runAs("person-a", () -> repository.findById("person-a")).orElseThrow();

        assertThat(found.getSteps()).extracting(OnboardingStepEntity::getStepKey).containsExactly("LOOK_AND_FEEL",
                "FIRST_SESSION");
    }

    private static OnboardingPathEntity pathFor(String subject) {

        return OnboardingPathEntity.builder().subject(subject).isNew(true).variant("ORGANIZER")
                .communitySlug("platform-engineering").anchorOrganizationId("org-1").anchorCommunityId(1L)
                .steps(List.of(
                        OnboardingStepEntity.builder().stepKey("LOOK_AND_FEEL")
                                .route("/communities/platform-engineering/look-and-feel").build(),
                        OnboardingStepEntity.builder().stepKey("FIRST_SESSION")
                                .route("/communities/platform-engineering/schedule").build()))
                .build();
    }
}
