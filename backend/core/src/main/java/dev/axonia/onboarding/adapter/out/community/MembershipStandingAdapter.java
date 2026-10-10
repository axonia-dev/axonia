package dev.axonia.onboarding.adapter.out.community;

import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.axonia.community.api.CommunityRole;
import dev.axonia.community.api.Memberships;
import dev.axonia.onboarding.application.port.out.MembershipStanding;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.security.CurrentToken;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class MembershipStandingAdapter implements MembershipStanding {

    private final Memberships memberships;

    @Override
    public Optional<Standing> standingIn(String slug) {

        try {
            var membership = memberships.myMembership(slug);
            return Optional.of(new Standing(membership.communityId(), CurrentToken.organization(),
                    membership.role() == CommunityRole.ORGANIZER));
        } catch (NotFoundException e) {
            // A slug that does not resolve, or a caller who belongs to nothing, is "no standing" and never a failure
            // the caller has to handle: assignment is best-effort by design. Anything else - a database hiccup, a
            // transaction failure - is not a business fact and must propagate, or a read-side outage during
            // assignment would freeze a wrong path with no signal.
            return Optional.empty();
        }
    }
}
