package dev.axonia.mentoring.application.port.out;

import java.util.List;
import java.util.Optional;

import dev.axonia.mentoring.domain.Availability;

public interface AvailabilityRepository {

    Optional<Availability> find(String org, long communityId, String userId);

    void upsert(String org, Availability availability);

    /** Active members of the community who take mentees or look for a mentor. */
    List<Availability> listedIn(String org, long communityId);

    /** The listing of one active member of the community, under the same rules as {@link #listedIn}. */
    Optional<Availability> findListed(String org, long communityId, String userId);

    void deleteIn(String org, long communityId, String userId);
}
