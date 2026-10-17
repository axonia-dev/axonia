package dev.axonia.recognition.application.port.out;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import dev.axonia.recognition.api.ContributionType;
import lombok.Builder;

@Builder
public record NewLoveQuery(String organizationId, String userId, ContributionType contributionType,
        Collection<Long> contributionIds, Instant floor, Instant asOf) {

    public NewLoveQuery {
        contributionIds = List.copyOf(contributionIds);
    }
}
