package dev.axonia.knowledge;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.knowledge.internal.ArtifactMapper;
import dev.axonia.knowledge.internal.ArtifactRepository;
import dev.axonia.knowledge.internal.ContributorResolver;
import lombok.RequiredArgsConstructor;

/**
 * Corrects whose work an artifact is. Separate from {@link ArtifactService#edit}, so an edit can never move credit. An
 * archived artifact is corrected too but projects nothing, having no signals while archived;
 * {@link ArtifactService#restore} republishes it under the corrected contributor.
 */
@Service
@RequiredArgsConstructor
public class ArtifactCreditService {

    private final ArtifactRepository artifactRepository;
    private final ArtifactMapper artifactMapper;
    private final ContributorResolver contributorResolver;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Artifact changeContributor(Long id, ChangeContributorRequest request) {

        var entity = ArtifactLookup.findOrThrow(artifactRepository, organizationContext.organizationId(), id);
        var contributorUserId = contributorResolver.resolveCorrection(entity.getCommunityId(), currentUser.userId(),
                request.contributor());
        if (contributorUserId.equals(entity.getContributorUserId())) {
            return artifactMapper.toArtifact(entity);
        }
        var saved = artifactRepository.save(entity.toBuilder().contributorUserId(contributorUserId).build());
        if (saved.isActive()) {
            eventPublisher.publishEvent(ArtifactEvents.contributed(saved));
        }
        return artifactMapper.toArtifact(saved);
    }
}
