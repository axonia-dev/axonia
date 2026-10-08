package dev.axonia.privacy.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.PolicyOpinions;
import dev.axonia.privacy.MemberDataRightsPolicy;
import lombok.RequiredArgsConstructor;

/**
 * Asks the contributed {@link MemberDataRightsPolicy}s, and falls back to the Enterprise answer: the Organization, not
 * its members, exercises them.
 */
@Component
@RequiredArgsConstructor
public class MemberDataRights {

    private final List<MemberDataRightsPolicy> policies;

    public boolean exercisedByMembers(String organizationId) {

        return PolicyOpinions.sole(policies, policy -> policy.exercisedByMembers(organizationId)).orElse(false);
    }
}
