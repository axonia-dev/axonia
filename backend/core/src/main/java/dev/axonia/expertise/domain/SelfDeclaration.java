package dev.axonia.expertise.domain;

import java.time.Instant;

import lombok.Builder;

/** One of the owner's declarations as their own profile shows it: the canonical tag in that community's vocabulary. */
@Builder
public record SelfDeclaration(String tag, long communityId, String communityName, String communitySlug, boolean hidden,
        Instant declaredAt) {
}
