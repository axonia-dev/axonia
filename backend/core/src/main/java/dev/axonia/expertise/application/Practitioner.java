package dev.axonia.expertise.application;

import java.time.Instant;

import dev.axonia.expertise.domain.Evidence;
import dev.axonia.expertise.domain.Tier;
import lombok.Builder;

/**
 * One person in a "who knows X?" answer. {@code openToQuestions} and {@code declaredAt} come from their earliest
 * declaration among the searched tags, {@code false} and {@code null} when they made none.
 */
@Builder
public record Practitioner(String userId, String displayName, String avatarUrl, String orgUnit, Tier tier,
        Evidence evidence, boolean openToQuestions, Instant declaredAt) {
}
