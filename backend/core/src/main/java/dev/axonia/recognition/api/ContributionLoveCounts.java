package dev.axonia.recognition.api;

import java.util.Collection;
import java.util.Map;

public interface ContributionLoveCounts {

    Map<Long, Long> countFor(String organizationId, ContributionType contributionType,
            Collection<Long> contributionIds);
}
