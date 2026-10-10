package dev.axonia.access.application;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.access.api.spi.DirectoryGroupsPolicy;
import dev.axonia.shared.api.policy.PolicyOpinions;
import lombok.RequiredArgsConstructor;

/** Asks the contributed {@link DirectoryGroupsPolicy}s, and falls back to the Enterprise answer: groups apply. */
@Component
@RequiredArgsConstructor
public class GroupBindingApplicability {

    private final List<DirectoryGroupsPolicy> policies;

    public boolean applyIn(String organizationId) {

        return PolicyOpinions.sole(policies, policy -> policy.groupsApply(organizationId)).orElse(true);
    }
}
