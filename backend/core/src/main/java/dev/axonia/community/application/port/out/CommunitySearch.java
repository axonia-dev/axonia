package dev.axonia.community.application.port.out;

import java.util.Set;

import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.MembershipFilter;
import lombok.Builder;

@Builder
public record CommunitySearch(String organizationId, CommunityStatus status, String name, MembershipFilter membership,
        Set<Long> memberOf) {

    public CommunitySearch {
        memberOf = Set.copyOf(memberOf);
    }
}
