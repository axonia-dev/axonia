package dev.axonia.community.adapter.out.persistence;

/** One grouped row of a per-community count query. */
record CommunityTally(long communityId, long n) {
}
