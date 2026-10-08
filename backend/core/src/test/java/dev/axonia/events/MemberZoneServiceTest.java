package dev.axonia.events;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.ForbiddenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@MockitoSettings
class MemberZoneServiceTest {

    private static final String ORG = "default";
    private static final String SLUG = "frontend-dojo";
    private static final long COMMUNITY_ID = 7L;

    @Mock
    private CommunityAccess communityAccess;

    @Mock
    private UserProfileAccess userProfileAccess;

    @Mock
    private OrganizationContext organizationContext;

    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private MemberZoneService sut;

    @BeforeEach
    void stubTheCommunity() {

        lenient().when(organizationContext.organizationId()).thenReturn(ORG);
        lenient().when(communityAccess.communityIdForSlug(SLUG)).thenReturn(COMMUNITY_ID);
        lenient().when(currentUser.userId()).thenReturn("me");
        lenient().when(communityAccess.isMember(COMMUNITY_ID, "me")).thenReturn(true);
    }

    @Test
    void countsTheMembersReadingEachClock() {

        // given
        when(communityAccess.memberUserIds(ORG, COMMUNITY_ID)).thenReturn(List.of("a", "b", "c"));
        when(userProfileAccess.timeZones(ORG, List.of("a", "b", "c")))
                .thenReturn(Map.of("a", "Europe/Berlin", "b", "Europe/Berlin", "c", "Asia/Kolkata"));

        // when
        var actual = sut.forCommunity(SLUG);

        // then - ordered by zone name, so one community always answers the same way
        assertThat(actual).containsExactly(new MemberZone("Asia/Kolkata", 1), new MemberZone("Europe/Berlin", 2));
    }

    /**
     * The answer states the zones a community spans, not its roster. A member nobody has measured belongs to no zone,
     * and a stand-in bucket for them would be a zone nobody is in.
     */
    @Test
    void aMemberWithNoClockIsAbsentRatherThanGrouped() {

        // given
        when(communityAccess.memberUserIds(ORG, COMMUNITY_ID)).thenReturn(List.of("a", "b"));
        when(userProfileAccess.timeZones(ORG, List.of("a", "b"))).thenReturn(Map.of("a", "Europe/Berlin"));

        // when
        var actual = sut.forCommunity(SLUG);

        // then
        assertThat(actual).containsExactly(new MemberZone("Europe/Berlin", 1));
    }

    @Test
    void aCommunityNobodyHasMeasuredAnswersEmpty() {

        // given
        when(communityAccess.memberUserIds(ORG, COMMUNITY_ID)).thenReturn(List.of("a"));
        when(userProfileAccess.timeZones(ORG, List.of("a"))).thenReturn(Map.of());

        // when
        var actual = sut.forCommunity(SLUG);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void aNonMemberIsRefused() {

        // given
        when(communityAccess.isMember(COMMUNITY_ID, "me")).thenReturn(false);

        // then
        assertThatThrownBy(() -> sut.forCommunity(SLUG)).isInstanceOf(ForbiddenException.class);
    }
}
