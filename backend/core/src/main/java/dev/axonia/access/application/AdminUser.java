package dev.axonia.access.application;

import java.time.Instant;
import java.util.List;

import dev.axonia.identity.api.UserDirectory;
import lombok.Builder;

@Builder
public record AdminUser(String userId, String displayName, String email, List<EffectiveRole> effectiveRoles,
        Instant joinedAt, Instant lastActiveAt, List<String> groups, UserDirectory.MemberStatus memberStatus) {
}
