package dev.axonia.community;

import jakarta.validation.constraints.NotNull;

public record UpdateContributionPolicyRequest(@NotNull Boolean canStartSeries, @NotNull Boolean canEditStructure,
        @NotNull Boolean canQueueTopics, @NotNull Boolean canManageSchedule) {
}
