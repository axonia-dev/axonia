package dev.axonia.onboarding.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.NotFoundException;
import dev.axonia.community.CommunityRole;
import dev.axonia.community.Membership;
import dev.axonia.community.MembershipService;
import dev.axonia.onboarding.MembershipStanding.Standing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@MockitoSettings
class MembershipStandingAdapterTest {

    @Mock
    private MembershipService memberships;

    @InjectMocks
    private MembershipStandingAdapter adapter;

    @BeforeEach
    void authenticate() {
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("gabriel").claim("org", "acme").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anOrganizerMembershipMapsToOrganizerStanding() {

        when(memberships.myMembership("guild"))
                .thenReturn(Membership.builder().communityId(7L).role(CommunityRole.ORGANIZER).build());

        assertThat(adapter.standingIn("guild")).contains(new Standing(7L, "acme", true));
    }

    @Test
    void aMemberMembershipMapsToNonOrganizerStanding() {

        when(memberships.myMembership("guild"))
                .thenReturn(Membership.builder().communityId(7L).role(CommunityRole.MEMBER).build());

        assertThat(adapter.standingIn("guild")).contains(new Standing(7L, "acme", false));
    }

    @Test
    void aNotFoundMembershipYieldsNoStanding() {

        when(memberships.myMembership("guild")).thenThrow(new NotFoundException("Membership not found"));

        assertThat(adapter.standingIn("guild")).isEmpty();
    }

    @Test
    void aDatabaseFailurePropagatesRatherThanBeingReadAsNoStanding() {

        when(memberships.myMembership("guild")).thenThrow(new DataAccessResourceFailureException("connection lost"));

        assertThatThrownBy(() -> adapter.standingIn("guild")).isInstanceOf(DataAccessResourceFailureException.class);
    }
}
