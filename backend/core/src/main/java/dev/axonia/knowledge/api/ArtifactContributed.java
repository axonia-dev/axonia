package dev.axonia.knowledge.api;

import java.util.List;

public record ArtifactContributed(Long artifactId, Long communityId, String contributorUserId, List<String> tags) {
}
