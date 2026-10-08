package dev.axonia.knowledge;

import java.util.List;

public record ArtifactContributed(Long artifactId, Long communityId, String contributorUserId, List<String> tags) {
}
