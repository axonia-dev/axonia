package dev.axonia.community.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.application.port.out.CommunityStore;
import dev.axonia.community.domain.CommunityRecord;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Looks a {@link CommunityRecord} up by tenant plus slug or id and turns a miss into a {@link NotFoundException}.
 * Shared by {@code CommunityService}, {@code CommunityPolicyService} and {@code CommunityCapabilityService}, so the
 * lookup and its not-found message live in one place for those three rather than being copied at each call site.
 * {@code MembershipService} and {@code LookAndFeelService} still carry their own by-slug copies and have not been
 * routed through here yet.
 */
@Component
@RequiredArgsConstructor
public class CommunityLookup {

    private final CommunityStore communityStore;

    @Transactional(readOnly = true)
    public CommunityRecord findBySlugOrThrow(String organizationId, String slug) {

        return communityStore.findBySlug(organizationId, slug)
                .orElseThrow(() -> new NotFoundException("Community not found: " + slug));
    }

    @Transactional(readOnly = true)
    public CommunityRecord findByIdOrThrow(String organizationId, Long id) {

        return communityStore.findById(organizationId, id)
                .orElseThrow(() -> new NotFoundException("Community not found: " + id));
    }
}
