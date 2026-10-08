package dev.axonia.community.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Looks a {@link CommunityEntity} up by tenant plus slug or id and turns a miss into a {@link NotFoundException}.
 * Shared by {@code CommunityService}, {@code CommunityPolicyService} and {@code CommunityCapabilities}, so the lookup
 * and its not-found message live in one place for those three rather than being copied at each call site.
 * {@code MembershipService} and {@code LookAndFeelService} still carry their own by-slug copies and have not been
 * routed through here yet.
 */
@Component
@RequiredArgsConstructor
public class Communities {

    private final CommunityRepository communityRepository;

    @Transactional(readOnly = true)
    public CommunityEntity findBySlugOrThrow(String organizationId, String slug) {

        return communityRepository.findByOrganizationIdAndSlug(organizationId, slug)
                .orElseThrow(() -> new NotFoundException("Community not found: " + slug));
    }

    @Transactional(readOnly = true)
    public CommunityEntity findByIdOrThrow(String organizationId, Long id) {

        return communityRepository.findByOrganizationIdAndId(organizationId, id)
                .orElseThrow(() -> new NotFoundException("Community not found: " + id));
    }
}
