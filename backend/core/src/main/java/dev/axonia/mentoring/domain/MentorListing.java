package dev.axonia.mentoring.domain;

import java.time.Instant;

/** A mentor's switch in one community, with when they were asked once and told once; either is null until then. */
public record MentorListing(long communityId, String userId, ListingSwitch state, Instant askedAt, Instant toldAt) {

    /** A mentor without a stored listing is at the default. */
    public static ListingSwitch switchOf(MentorListing listing) {

        return listing == null ? ListingSwitch.DEFAULT : listing.state();
    }
}
