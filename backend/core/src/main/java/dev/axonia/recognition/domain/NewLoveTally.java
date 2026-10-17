package dev.axonia.recognition.domain;

import dev.axonia.recognition.api.ContributionType;
import lombok.Builder;

/** Loves one contribution received since its community's marker; capped when the floor cut the window short. */
@Builder
public record NewLoveTally(ContributionType contributionType, Long contributionId, Long communityId, long newLoves,
        boolean capped) {
}
