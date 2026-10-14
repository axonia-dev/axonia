package dev.axonia.community.application.port.out;

import java.util.Set;

import lombok.Builder;

import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.MembershipFilter;

@Builder
public record CommunitySearch(String organizationId, CommunityStatus status, String name, MembershipFilter membership,
        Set<Long> memberOf) {
}
