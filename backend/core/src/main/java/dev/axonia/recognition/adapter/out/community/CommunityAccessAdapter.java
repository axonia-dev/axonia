package dev.axonia.recognition.adapter.out.community;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.axonia.community.api.Communities;
import dev.axonia.community.api.Memberships;
import dev.axonia.recognition.application.port.out.CommunityAccess;
import dev.axonia.recognition.domain.CommunitySummary;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

@Component("recognitionCommunityAccessAdapter")
@RequiredArgsConstructor
class CommunityAccessAdapter implements CommunityAccess {

    private final Communities communityService;
    private final Memberships membershipService;

    @Override
    public boolean isMember(long communityId, String userId) {

        return membershipService.isMember(communityId, userId);
    }

    @Override
    public Map<Long, CommunitySummary> summaryById(Collection<Long> communityIds) {

        var summaries = new LinkedHashMap<Long, CommunitySummary>();
        for (var id : communityIds.stream().distinct().toList()) {
            try {
                var community = communityService.getById(id);
                summaries.put(id, new CommunitySummary(community.slug(), community.name()));
            } catch (NotFoundException ignored) {
                // A community removed after the contribution was recorded simply has no link target.
            }
        }
        return summaries;
    }
}
