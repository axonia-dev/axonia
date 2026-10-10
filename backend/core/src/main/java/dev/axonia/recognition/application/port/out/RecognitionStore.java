package dev.axonia.recognition.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.domain.Recognition;

/** Where recognized contributions are kept. */
public interface RecognitionStore {

    Optional<Recognition> find(String organizationId, ContributionType contributionType, Long contributionId);

    List<Recognition> findAll(String organizationId, ContributionType contributionType,
            Collection<Long> contributionIds);

    /**
     * The recognition already held for the candidate's contribution, or the candidate stored as a new one. A concurrent
     * first love that stored it first wins, and its recognition is the answer.
     */
    Recognition recognize(Recognition candidate);
}
