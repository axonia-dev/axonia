package dev.axonia.events.application;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.events.api.SessionMother;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.events.domain.SessionRecordMother;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class SessionNamingServiceTest {

    private static final long COMMUNITY = 3L;

    @Mock
    private SessionStore sessionStore;
    @Mock
    private SessionMapper sessionMapper;
    @Mock
    private SessionAuthorizer sessionAuthorizer;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private OrganizationContext organizationContext;

    private SessionNamingService sut;

    @BeforeEach
    void wire() {

        sut = new SessionNamingService(sessionStore, sessionMapper, sessionAuthorizer, currentUser,
                organizationContext);
    }

    private SessionRecord stored(SessionRecord session) {

        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionStore.findByOrganizationIdAndId("default", session.getId())).thenReturn(Optional.of(session));
        return session;
    }

    private void organizer() {

        when(currentUser.userId()).thenReturn("orga");
    }

    private void savesAndMaps() {

        when(sessionStore.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionMapper.toSessionDetail(any())).thenAnswer(
                invocation -> SessionMother.any().title(invocation.<SessionRecord>getArgument(0).getTitle()).build());
    }

    @Test
    void renamesTheSessionWithItsTitleTrimmedAndAnswersTheDetail() {

        stored(SessionRecordMother.any().id(4L).communityId(COMMUNITY).build());
        organizer();
        savesAndMaps();

        var actual = sut.rename(4L, "  S26E10 - DevTool ");

        verify(sessionAuthorizer).requireScheduleManager(COMMUNITY, "orga");
        verify(sessionStore).save(argThat(saved -> "S26E10 - DevTool".equals(saved.getTitle())));
        assertThat(actual.title()).isEqualTo("S26E10 - DevTool");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void aBlankOrMissingTitleClearsTheSessionsOwn(String title) {

        stored(SessionRecordMother.any().id(4L).title("S26E10 - DevTool").build());
        organizer();
        savesAndMaps();

        var actual = sut.rename(4L, title);

        verify(sessionStore).save(argThat(saved -> saved.getTitle() == null));
        assertThat(actual.title()).isNull();
    }

    /** Naming after the fact is the main use: a backfilled history is named once it is all in. */
    @Test
    void renamesAHeldPastSessionAndLeavesItHeld() {

        stored(SessionRecordMother.any().id(4L).status("HELD").scheduledAt(Instant.parse("2024-02-01T17:00:00Z"))
                .closedAt(Instant.parse("2024-02-01T19:00:00Z")).build());
        organizer();
        savesAndMaps();

        sut.rename(4L, "S24E02 - Kafka");

        verify(sessionStore).save(argThat(saved -> "S24E02 - Kafka".equals(saved.getTitle())
                && "HELD".equals(saved.getStatus()) && saved.getClosedAt() != null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "SKIPPED"})
    void renamesASessionThatDidNotHappenToo(String status) {

        stored(SessionRecordMother.any().id(4L).status(status).build());
        organizer();
        savesAndMaps();

        sut.rename(4L, "Called off: venue");

        verify(sessionStore).save(
                argThat(saved -> "Called off: venue".equals(saved.getTitle()) && status.equals(saved.getStatus())));
    }

    @Test
    void refusesSomebodyWhoCannotManageTheSchedule() {

        stored(SessionRecordMother.any().id(4L).communityId(COMMUNITY).build());
        when(currentUser.userId()).thenReturn("member");
        doThrow(new ForbiddenException("Managing the schedule requires schedule-management rights"))
                .when(sessionAuthorizer).requireScheduleManager(COMMUNITY, "member");

        assertThatThrownBy(() -> sut.rename(4L, "Mine now")).isInstanceOf(ForbiddenException.class);
        verify(sessionStore, never()).save(any());
    }

    @Test
    void refusesATitleLongerThanTheColumnBeforeWritingAnything() {

        stored(SessionRecordMother.any().id(4L).build());
        organizer();

        assertThatThrownBy(() -> sut.rename(4L, "x".repeat(256))).isInstanceOf(ValidationException.class);
        verify(sessionStore, never()).save(any());
    }

    @Test
    void anUnknownSessionIsNotFound() {

        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionStore.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.rename(99L, "Anything")).isInstanceOf(NotFoundException.class);
    }
}
