package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.mentoring.api.ListingRequest;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.MentorListingRepository;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.domain.ListingChange;
import dev.axonia.mentoring.domain.ListingSwitch;
import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MentorListingServiceTest {

    private static final String ORG = "org-1";
    private static final String SLUG = "platform";
    private static final String ME = "me";
    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00.123456Z");
    private static final Instant NOW_MILLIS = Instant.parse("2026-11-24T10:00:00.123Z");

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private MentorListingRepository repository;

    private MentorListingService service;

    @BeforeEach
    void setUp() {

        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(ME);
        when(communityAccess.communityForSlug(SLUG)).thenReturn(community(true, false));
        when(communityAccess.isMember(7L, ME)).thenReturn(true);
        service = new MentorListingService(organizationContext, currentUser, communityAccess, repository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MentoringCommunity community(boolean enabled, boolean archived) {

        return new MentoringCommunity(7L, SLUG, "Platform", archived, enabled);
    }

    @Test
    void setOnAndOff() {

        // when
        service.set(SLUG, new ListingRequest(true));
        service.set(SLUG, new ListingRequest(false));

        // then
        verify(repository).setSwitch(ORG,
                ListingChange.builder().communityId(7L).userId(ME).state(ListingSwitch.ON).at(NOW_MILLIS).build());
        verify(repository).setSwitch(ORG,
                ListingChange.builder().communityId(7L).userId(ME).state(ListingSwitch.OFF).at(NOW_MILLIS).build());
    }

    @Test
    void refusesNonMember() {

        // given
        when(communityAccess.communityForSlug(SLUG)).thenReturn(community(false, true));
        when(communityAccess.isMember(7L, ME)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> service.set(SLUG, new ListingRequest(true))).isInstanceOf(ForbiddenException.class);
        verify(repository, never()).setSwitch(any(), any());
    }

    @Test
    void refusesDelegated() {

        // given
        when(currentUser.delegated()).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.set(SLUG, new ListingRequest(true)))
                .isInstanceOf(OwnSignInRequiredException.class);
        verify(repository, never()).setSwitch(any(), any());
    }

    @Test
    void refusesWhenMentoringOff() {

        // given
        when(communityAccess.communityForSlug(SLUG)).thenReturn(community(false, false));

        // when / then
        assertThatThrownBy(() -> service.set(SLUG, new ListingRequest(true))).isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "MENTORING_OFF");
        verify(repository, never()).setSwitch(any(), any());
    }

    @Test
    void refusesInAnArchivedCommunity() {

        // given
        when(communityAccess.communityForSlug(SLUG)).thenReturn(community(true, true));

        // when / then
        assertThatThrownBy(() -> service.set(SLUG, new ListingRequest(false))).isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "COMMUNITY_ARCHIVED");
        verify(repository, never()).setSwitch(any(), any());
    }
}
