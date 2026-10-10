package dev.axonia.knowledge.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.knowledge.api.Artifact;
import dev.axonia.knowledge.application.port.out.ArtifactStore;
import dev.axonia.knowledge.application.port.out.CurrentUser;
import dev.axonia.knowledge.application.port.out.OrganizationContext;
import lombok.RequiredArgsConstructor;

/**
 * Corrects whose work an artifact is. Separate from {@link ArtifactService#edit}, so an edit can never move credit. An
 * archived artifact is corrected too but projects nothing, having no signals while archived;
 * {@link ArtifactService#restore} republishes it under the corrected contributor.
 */
@Service
@RequiredArgsConstructor
public class ArtifactCreditService {

    private final ArtifactStore artifactStore;
    private final ArtifactMapper artifactMapper;
    private final ContributorResolver contributorResolver;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Artifact changeContributor(Long id, ChangeContributorRequest request) {

        var entity = ArtifactLookup.findOrThrow(artifactStore, organizationContext.organizationId(), id);
        var contributorUserId = contributorResolver.resolveCorrection(entity.communityId(), currentUser.userId(),
                request.contributor());
        if (contributorUserId.equals(entity.contributorUserId())) {
            return artifactMapper.toArtifact(entity);
        }
        var saved = artifactStore.save(entity.toBuilder().contributorUserId(contributorUserId).build());
        if (saved.isActive()) {
            eventPublisher.publishEvent(ArtifactEvents.contributed(saved));
        }
        return artifactMapper.toArtifact(saved);
    }
}
