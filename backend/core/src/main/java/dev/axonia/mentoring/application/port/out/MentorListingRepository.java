package dev.axonia.mentoring.application.port.out;

import java.time.Instant;
import java.util.Map;

import dev.axonia.mentoring.domain.ListingChange;
import dev.axonia.mentoring.domain.MentorListing;

public interface MentorListingRepository {

    /** Every stored listing of the community, by user id; a mentor without a row is at the default. */
    Map<String, MentorListing> in(String org, long communityId);

    void setSwitch(String org, ListingChange change);

    /** Keeps the first moment they were asked. */
    void markAsked(String org, long communityId, String userId, Instant at);

    /** Keeps the first moment they were told. */
    void markTold(String org, long communityId, String userId, Instant at);
}
