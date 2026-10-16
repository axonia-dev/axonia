package dev.axonia.recognition.application;

import java.util.Collection;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.recognition.api.ContributionLoveCounts;
import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.application.port.out.LoveTallies;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoveCountService implements ContributionLoveCounts {

    private final LoveTallies loveTallies;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> countFor(String organizationId, ContributionType contributionType,
            Collection<Long> contributionIds) {

        if (contributionIds.isEmpty()) {
            return Map.of();
        }
        return loveTallies.countFor(organizationId, contributionType, contributionIds);
    }
}
