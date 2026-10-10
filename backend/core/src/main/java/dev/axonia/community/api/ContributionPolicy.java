package dev.axonia.community.api;

public record ContributionPolicy(boolean canStartSeries, boolean canEditStructure, boolean canQueueTopics,
        boolean canManageSchedule) {

    public static ContributionPolicy curated() {

        return new ContributionPolicy(false, false, false, false);
    }
}
