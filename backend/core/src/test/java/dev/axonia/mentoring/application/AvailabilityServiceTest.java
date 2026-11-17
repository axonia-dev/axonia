package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.mentoring.api.Availability;
import dev.axonia.mentoring.api.AvailabilityRequest;
import dev.axonia.mentoring.application.port.out.AvailabilityRepository;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AvailabilityServiceTest {

    private static final String ORG = "org-1";
    private static final String SLUG = "platform";
    private static final String ME = "me";
    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00.123456Z");

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private AvailabilityRepository repository;

    private AvailabilityService service;

    @BeforeEach
    void setUp() {

        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(ME);
        when(communityAccess.communityForSlug(SLUG)).thenReturn(community(true, false));
        when(communityAccess.isMember(7L, ME)).thenReturn(true);
        service = new AvailabilityService(organizationContext, currentUser, communityAccess, repository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MentoringCommunity community(boolean enabled, boolean archived) {

        return new MentoringCommunity(7L, SLUG, "Platform", archived, enabled);
    }

    @Test
    void aMemberSetsTheirAvailability() {

        // given
        var request = new AvailabilityRequest(true, 3, true);

        // when
        var actual = service.set(SLUG, request);

        // then
        assertThat(actual).isEqualTo(new Availability(true, 3, true));
        var saved = ArgumentCaptor.forClass(dev.axonia.mentoring.domain.Availability.class);
        verify(repository).upsert(eq(ORG), saved.capture());
        assertThat(saved.getValue().updatedAt()).isEqualTo(Instant.parse("2026-11-16T10:00:00.123Z"));
        assertThat(saved.getValue().communityId()).isEqualTo(7L);
        assertThat(saved.getValue().userId()).isEqualTo(ME);
    }

    @Test
    void noLimitIsAccepted() {

        // when
        var actual = service.set(SLUG, new AvailabilityRequest(true, null, false));

        // then
        assertThat(actual).isEqualTo(new Availability(true, null, false));
    }

    @Test
    void menteeLimitOutOfRangeIsRefused() {

        // when / then
        for (var limit : new int[]{0, 11}) {
            assertThatThrownBy(() -> service.set(SLUG, new AvailabilityRequest(true, limit, false)))
                    .isInstanceOf(ValidationException.class);
        }
        verify(repository, never()).upsert(any(), any());
    }

    @Test
    void theBoundsOfTheLimitAreAccepted() {

        // when / then
        assertThat(service.set(SLUG, new AvailabilityRequest(true, 1, false)).menteeLimit()).isEqualTo(1);
        assertThat(service.set(SLUG, new AvailabilityRequest(true, 10, false)).menteeLimit()).isEqualTo(10);
    }

    @Test
    void aSwitchedOffGuildRefusesAvailability() {

        // given
        when(communityAccess.communityForSlug(SLUG)).thenReturn(community(false, false));

        // when / then
        assertThatThrownBy(() -> service.set(SLUG, new AvailabilityRequest(true, null, false)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("switched off");
    }

    @Test
    void anArchivedGuildRefusesNewAvailability() {

        // given
        when(communityAccess.communityForSlug(SLUG)).thenReturn(community(true, true));

        // when / then
        assertThatThrownBy(() -> service.set(SLUG, new AvailabilityRequest(true, null, false)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("archived");
    }

    @Test
    void aNonMemberCannotSetAvailability() {

        // given
        when(communityAccess.isMember(7L, ME)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> service.set(SLUG, new AvailabilityRequest(true, null, false)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aNonMemberIsRefusedAsSuchEvenInASwitchedOffOrArchivedGuild() {

        // given
        when(communityAccess.communityForSlug(SLUG)).thenReturn(community(false, true));
        when(communityAccess.isMember(7L, ME)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> service.set(SLUG, new AvailabilityRequest(true, null, false)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aDelegatedSessionCannotSetAvailability() {

        // given
        when(currentUser.delegated()).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.set(SLUG, new AvailabilityRequest(true, null, false)))
                .isInstanceOf(OwnSignInRequiredException.class);
        verify(repository, never()).upsert(any(), any());
    }
}
