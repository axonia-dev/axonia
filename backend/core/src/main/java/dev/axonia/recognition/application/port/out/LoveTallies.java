package dev.axonia.recognition.application.port.out;

import java.util.Collection;
import java.util.Map;

import dev.axonia.recognition.api.ContributionType;

public interface LoveTallies {

    Map<Long, Long> countFor(String organizationId, ContributionType contributionType,
            Collection<Long> contributionIds);
}
