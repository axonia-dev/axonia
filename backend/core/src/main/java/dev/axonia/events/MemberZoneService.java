package dev.axonia.events;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ForbiddenException;
import lombok.RequiredArgsConstructor;

/**
 * The zones a community's members read, and how many read each.
 *
 * <p>
 * Deliberately carries no candidate time, and there is no rule here to apply to one: the product states the spread and
 * grades nothing. So the projection of a particular hour into each zone belongs in the browser, where the organizer is
 * moving that hour, and this answer is the same for every reader of a given community - which is what makes moving the
 * hour in a form cost no request.
 *
 * <p>
 * Members with no clock are absent rather than grouped under a stand-in. The answer is a statement about the zones a
 * community spans, not a census of its roster, and a stand-in bucket would be a zone nobody is in.
 */
@Service
@RequiredArgsConstructor
public class MemberZoneService {

    private final CommunityAccess communityAccess;
    private final UserProfileAccess userProfileAccess;
    private final OrganizationContext organizationContext;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<MemberZone> forCommunity(String slug) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        if (!communityAccess.isMember(communityId, currentUser.userId())) {
            throw new ForbiddenException("Only a member may read this community's zones");
        }

        var memberIds = communityAccess.memberUserIds(organizationId, communityId);
        return userProfileAccess.timeZones(organizationId, memberIds).values().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
                .map(entry -> new MemberZone(entry.getKey(), entry.getValue().intValue()))
                .sorted(Comparator.comparing(MemberZone::zone)).toList();
    }
}
