package dev.axonia.recognition.internal;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.axonia.NotFoundException;
import dev.axonia.community.CommunityService;
import dev.axonia.community.MembershipService;
import dev.axonia.recognition.CommunityAccess;
import dev.axonia.recognition.CommunitySummary;
import lombok.RequiredArgsConstructor;

@Component("recognitionCommunityAccessAdapter")
@RequiredArgsConstructor
class CommunityAccessAdapter implements CommunityAccess {

    private final CommunityService communityService;
    private final MembershipService membershipService;

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
