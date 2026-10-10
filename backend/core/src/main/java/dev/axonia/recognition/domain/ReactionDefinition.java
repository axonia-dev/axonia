package dev.axonia.recognition.domain;

import lombok.Builder;

/**
 * A custom reaction as a caller sees it. {@code proposedByUserId} is populated only for a pending row read by somebody
 * holding {@code reaction:read}: an approved reaction belongs to the Organization, and who first suggested it is not
 * part of what the picker says. {@code usageCount} is populated only by {@code ReactionCatalogService#all()} - the
 * administration read a removal decision needs a number for - and is always {@code 0} everywhere else, including the
 * picker's {@code ReactionCatalogService#approved()}.
 */
@Builder
public record ReactionDefinition(Long id, String name, String imageUrl, boolean pending, String proposedByUserId,
        long usageCount) {
}
