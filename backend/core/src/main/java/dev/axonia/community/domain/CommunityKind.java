package dev.axonia.community.domain;

import lombok.Builder;

@Builder
public record CommunityKind(Long id, String label, long communityCount) {
}
