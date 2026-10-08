package dev.axonia.community;

import lombok.Builder;

@Builder
public record CommunityKind(Long id, String label, long communityCount) {
}
