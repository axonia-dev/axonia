package dev.axonia.knowledge.application;

import org.springframework.stereotype.Service;

import dev.axonia.knowledge.application.port.out.ArtifactLoveCounts;
import dev.axonia.recognition.api.ContributionLoved;
import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.api.ContributionUnloved;
import lombok.RequiredArgsConstructor;

/**
 * Keeps {@code artifact.love_count} in step with recognition's loves. Only {@link ContributionType#ARTIFACT_CAPTURED}
 * contributions correlate to an artifact, so other contribution kinds are ignored.
 */
@Service
@RequiredArgsConstructor
public class ArtifactLoveCounter {

    private final ArtifactLoveCounts loveCounts;

    public void loved(ContributionLoved event) {

        if (event.contributionType() != ContributionType.ARTIFACT_CAPTURED) {
            return;
        }
        loveCounts.increment(event.contributionId(), event.communityId());
    }

    public void unloved(ContributionUnloved event) {

        if (event.contributionType() != ContributionType.ARTIFACT_CAPTURED) {
            return;
        }
        loveCounts.decrement(event.contributionId(), event.communityId());
    }
}
