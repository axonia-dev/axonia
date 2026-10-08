package dev.axonia.community;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.internal.CommunityEntity;
import dev.axonia.community.internal.CommunityKindRepository;
import dev.axonia.community.internal.CommunityRepository;
import dev.axonia.community.internal.MembershipEntity;
import dev.axonia.community.internal.MembershipRepository;
import lombok.RequiredArgsConstructor;

/**
 * Provisions a hub's single community and enrolls members into it, called from hub provisioning/joining inside their
 * respective transactions, under the hub's RLS scope. A self-serve hub is strictly 1:1 with its community: same name,
 * slug = hub slug, description seeded from the tagline, creator bound as ORGANIZER. Vocabulary seeding
 * (CommunityKindDefaults) must have run first — the community is born with the seeded "Community of Practice" kind.
 */
@Service
@RequiredArgsConstructor
public class CommunityProvisioning {

    static final String INITIAL_KIND_LABEL = "Community of Practice";

    private final CommunityRepository communityRepository;
    private final CommunityKindRepository kindRepository;
    private final MembershipRepository membershipRepository;

    @Transactional
    public Community provisionInitial(ProvisionCommunityRequest request) {
        var organizationId = request.organizationId();
        var kind = kindRepository.findByOrganizationIdOrderByLabelAsc(organizationId).stream()
                .filter(candidate -> INITIAL_KIND_LABEL.equals(candidate.getLabel())).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Hub vocabulary missing the '" + INITIAL_KIND_LABEL + "' kind for: " + organizationId));
        var saved = communityRepository
                .save(CommunityEntity.builder().organizationId(organizationId).slug(request.slug()).name(request.name())
                        .kindId(kind.getId()).description(request.tagline() == null ? "" : request.tagline())
                        .status(CommunityStatus.ACTIVE).build());
        membershipRepository.save(MembershipEntity.builder().organizationId(organizationId).communityId(saved.getId())
                .userId(request.creatorUserId()).role(CommunityRole.ORGANIZER).build());
        return Community.builder().id(saved.getId()).slug(saved.getSlug()).name(saved.getName()).kindId(kind.getId())
                .kind(kind.getLabel()).description(saved.getDescription()).status(saved.getStatus()).memberCount(1L)
                .createdAt(saved.getCreatedAt()).updatedAt(saved.getUpdatedAt()).build();
    }

    /**
     * Enrolls a hub joiner as MEMBER of the hub's single community (slug == organization id, the 1:1 invariant). Called
     * from hub join inside the join transaction, under the hub's RLS scope. Idempotent: an existing membership is left
     * untouched, so a legacy joiner who already holds one can never fail the hub join. Returns {@code true} when a new
     * membership row was inserted and {@code false} when one already existed, so the caller moves the hub member count
     * only on a real enrollment.
     */
    @Transactional
    public boolean enrollJoiner(String organizationId, String userId) {
        var community = communityRepository.findByOrganizationIdAndSlug(organizationId, organizationId)
                .orElseThrow(() -> new IllegalStateException("Hub community missing for: " + organizationId));
        if (membershipRepository.findByOrganizationIdAndCommunityIdAndUserId(organizationId, community.getId(), userId)
                .isPresent()) {
            return false;
        }
        membershipRepository.save(MembershipEntity.builder().organizationId(organizationId)
                .communityId(community.getId()).userId(userId).role(CommunityRole.MEMBER).build());
        return true;
    }
}
