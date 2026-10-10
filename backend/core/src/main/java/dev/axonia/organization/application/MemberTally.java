package dev.axonia.organization.application;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.organization.api.spi.DirectoryHeadcount;
import dev.axonia.organization.api.spi.MemberCountPolicy;
import dev.axonia.shared.api.policy.PolicyOpinions;
import lombok.RequiredArgsConstructor;

/**
 * Asks the contributed {@link MemberCountPolicy}s, and falls back to counting the Organization's directory.
 */
@Component
@RequiredArgsConstructor
public class MemberTally {

    private final List<MemberCountPolicy> policies;
    private final DirectoryHeadcount directoryHeadcount;

    public long of(String organizationId) {

        return PolicyOpinions.sole(policies, policy -> policy.memberCount(organizationId))
                .orElseGet(() -> directoryHeadcount.presentMembers(organizationId));
    }
}
