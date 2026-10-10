package dev.axonia.community.adapter.out.roundup;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.adapter.out.persistence.CommunityRepository;
import dev.axonia.community.api.CommunityStatus;
import dev.axonia.roundup.api.spi.CommunityActivity;
import lombok.RequiredArgsConstructor;

/**
 * Community's implementation of the roundup's community port. The organization arrives as a parameter rather than
 * through this module's request-scoped {@code OrganizationContext}, because a roundup is assembled with no JWT in
 * scope.
 */
@Component
@RequiredArgsConstructor
class CommunityActivityAdapter implements CommunityActivity {

    private final CommunityRepository communityRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CommunitySummary> activeCommunities(String organizationId) {

        return communityRepository.findByOrganizationIdAndStatusOrderByNameAsc(organizationId, CommunityStatus.ACTIVE)
                .stream().map(entity -> new CommunitySummary(entity.getId(), entity.getName(), entity.getBadgeKey(),
                        entity.getAccentColor()))
                .toList();
    }
}
