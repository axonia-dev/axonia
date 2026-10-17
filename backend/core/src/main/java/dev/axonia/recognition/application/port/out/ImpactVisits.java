package dev.axonia.recognition.application.port.out;

import java.time.Instant;
import java.util.Collection;

public interface ImpactVisits {

    /** Moves each community's marker to asOf unless it is already later. */
    void markSeen(String organizationId, String userId, Collection<Long> communityIds, Instant asOf);
}
