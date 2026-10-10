package dev.axonia.community.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.Community;
import dev.axonia.community.api.CommunityRole;
import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.ProvisionCommunityRequest;
import dev.axonia.community.application.port.out.CommunityKindStore;
import dev.axonia.community.application.port.out.CommunityStore;
import dev.axonia.community.application.port.out.MembershipLedger;
import dev.axonia.community.domain.CommunityRecord;
import dev.axonia.community.domain.MembershipRecord;
import lombok.RequiredArgsConstructor;

/**
 * Provisions a hub's single community and enrolls members into it, called from hub provisioning/joining inside their
 * respective transactions, under the hub's RLS scope. A self-serve hub is strictly 1:1 with its community: same name,
 * slug = hub slug, description seeded from the tagline, creator bound as ORGANIZER. Vocabulary seeding
 * (CommunityKindDefaultsService) must have run first — the community is born with the seeded "Community of Practice"
 * kind.
 */
@Service
@RequiredArgsConstructor
public class CommunityProvisioningService implements dev.axonia.community.api.CommunityProvisioning {

    static final String INITIAL_KIND_LABEL = "Community of Practice";

    private final CommunityStore communityStore;
    private final CommunityKindStore communityKindStore;
    private final MembershipLedger membershipLedger;

    @Override
    @Transactional
    public Community provisionInitial(ProvisionCommunityRequest request) {
        var organizationId = request.organizationId();
        var kind = communityKindStore.listByLabel(organizationId).stream()
                .filter(candidate -> INITIAL_KIND_LABEL.equals(candidate.getLabel())).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Hub vocabulary missing the '" + INITIAL_KIND_LABEL + "' kind for: " + organizationId));
        var saved = communityStore
                .save(CommunityRecord.builder().organizationId(organizationId).slug(request.slug()).name(request.name())
                        .kindId(kind.getId()).description(request.tagline() == null ? "" : request.tagline())
                        .status(CommunityStatus.ACTIVE).build());
        membershipLedger.save(MembershipRecord.builder().organizationId(organizationId).communityId(saved.getId())
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
    @Override
    @Transactional
    public boolean enrollJoiner(String organizationId, String userId) {
        var community = communityStore.findBySlug(organizationId, organizationId)
                .orElseThrow(() -> new IllegalStateException("Hub community missing for: " + organizationId));
        if (membershipLedger.find(organizationId, community.getId(), userId).isPresent()) {
            return false;
        }
        membershipLedger.save(MembershipRecord.builder().organizationId(organizationId).communityId(community.getId())
                .userId(userId).role(CommunityRole.MEMBER).build());
        return true;
    }
}
