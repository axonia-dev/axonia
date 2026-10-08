package dev.axonia.access.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.PolicyOpinions;
import dev.axonia.access.DirectoryGroupsPolicy;
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
