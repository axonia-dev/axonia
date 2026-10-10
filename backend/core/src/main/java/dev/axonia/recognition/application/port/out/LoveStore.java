package dev.axonia.recognition.application.port.out;

import java.util.List;
import java.util.Optional;

import dev.axonia.recognition.domain.GivenLove;

/** Where the loves members give are kept. */
public interface LoveStore {

    Optional<GivenLove> find(String organizationId, Long recognizedContributionId, String loverUserId);

    List<GivenLove> ofRecognition(String organizationId, Long recognizedContributionId);

    /** Stores a new love; empty when this lover has already loved this contribution. */
    Optional<GivenLove> add(GivenLove love);

    void remove(GivenLove love);
}
