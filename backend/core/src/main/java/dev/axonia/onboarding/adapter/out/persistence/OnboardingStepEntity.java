package dev.axonia.onboarding.adapter.out.persistence;

import java.time.Instant;

import org.springframework.data.relational.core.mapping.Table;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.With;

/**
 * One frozen step. {@code route} is the exact path a visit is matched against, and {@code detail} is the live content
 * the step pointed at when it was assembled (an artifact's title, a session's date), null for a step that names a place
 * rather than a thing.
 *
 * <p>
 * {@code subject} is absent as a field: Spring Data JDBC writes it as the back-reference of the parent aggregate's
 * {@code @MappedCollection}, and a second mapping of the same column would fight it.
 */
@Getter
@Builder(toBuilder = true)
@ToString
@Table("onboarding_step")
class OnboardingStepEntity {

    private String stepKey;

    private String route;

    private String detail;

    @With
    private Instant visitedAt;
}
