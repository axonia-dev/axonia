package dev.axonia.community.adapter.out.persistence;

/** One member of one community, as a per-community roster query returns it. */
record CommunityFace(long communityId, String userId) {
}
