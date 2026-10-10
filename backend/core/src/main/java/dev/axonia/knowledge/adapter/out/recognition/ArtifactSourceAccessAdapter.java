package dev.axonia.knowledge.adapter.out.recognition;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.knowledge.adapter.out.persistence.ArtifactEntity;
import dev.axonia.knowledge.adapter.out.persistence.ArtifactRepository;
import dev.axonia.recognition.api.ContributionSource;
import dev.axonia.recognition.api.spi.ArtifactSourceAccess;
import lombok.RequiredArgsConstructor;

/** Knowledge-owned adapter answering recognition's need for authoritative artifact attribution. */
@Component
@RequiredArgsConstructor
class ArtifactSourceAccessAdapter implements ArtifactSourceAccess {

    private final ArtifactRepository artifactRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ContributionSource> artifactSource(String organizationId, Long artifactId) {

        return artifactRepository.findByOrganizationIdAndId(organizationId, artifactId)
                .map(a -> ContributionSource.builder().communityId(a.getCommunityId())
                        .contributorUserIds(List.of(a.getContributorUserId())).title(a.getTitle())
                        .occurredAt(a.getCreatedAt()).build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> artifactsCapturedBy(String organizationId, String userId) {

        return artifactRepository.findByOrganizationIdAndContributorUserId(organizationId, userId).stream()
                .map(ArtifactEntity::getId).toList();
    }
}
