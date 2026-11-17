package dev.axonia.communication.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.communication.api.InAppEntryShape;
import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.application.port.out.CurrentUser;
import dev.axonia.communication.application.port.out.NotificationCentreReads;
import dev.axonia.communication.domain.NotificationPage;
import dev.axonia.communication.domain.PanelViewer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationCentreServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final String USER_ID = "user-1";
    private static final PanelViewer MEMBER = PanelViewer.member(USER_ID);

    @Mock
    NotificationCentreReads reads;
    @Mock
    CurrentUser currentUser;

    NotificationCentreService service;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var builders = new NotificationEntryBuilders(
                List.of(new InAppEntryShape(MessageType.of("personal_type"), "line", "url", true)));
        service = new NotificationCentreService(reads, currentUser, builders, clock);
    }

    /**
     * Below the floor: a non-positive size is clamped up to 1 rather than passed through as-is. The repository already
     * short-circuits a non-positive size to an empty page, but this clamp is the API's own contract, not a reliance on
     * that guard.
     */
    @Test
    void pageClampsASizeBelowTheFloorToOne() {

        when(currentUser.userId()).thenReturn(USER_ID);
        when(reads.page(MEMBER, null, null, 1)).thenReturn(new NotificationPage(List.of(), false, null, null));

        service.page(null, null, 0);

        verify(reads).page(MEMBER, null, null, 1);
    }

    /** Above the ceiling: a hostile size=100000 is bounded down to the max page size before it reaches the read. */
    @Test
    void pageClampsASizeAboveTheCeilingToTheMax() {

        when(currentUser.userId()).thenReturn(USER_ID);
        when(reads.page(MEMBER, null, null, 50)).thenReturn(new NotificationPage(List.of(), false, null, null));

        service.page(null, null, 100_000);

        verify(reads).page(MEMBER, null, null, 50);
    }

    @Test
    void pagePassesTheCursorThroughUnchanged() {

        var before = Instant.parse("2026-08-01T10:00:00Z");
        when(currentUser.userId()).thenReturn(USER_ID);
        when(reads.page(MEMBER, before, 7L, 20)).thenReturn(new NotificationPage(List.of(), false, null, null));

        service.page(before, 7L, 20);

        verify(reads).page(MEMBER, before, 7L, 20);
    }

    @Test
    void unreadCountReadsForTheSignedInMember() {

        when(currentUser.userId()).thenReturn(USER_ID);
        when(reads.unreadCount(MEMBER)).thenReturn(4);

        assertThat(service.unreadCount()).isEqualTo(4);
    }

    @Test
    void aSessionActingForTheMemberReadsThePanelWithoutTheTypesPrivateToThem() {

        // given
        var actingFor = new PanelViewer(USER_ID, Set.of("personal_type"));
        when(currentUser.userId()).thenReturn(USER_ID);
        when(currentUser.delegated()).thenReturn(true);
        when(reads.page(actingFor, null, null, 20)).thenReturn(new NotificationPage(List.of(), false, null, null));
        when(reads.unreadCount(actingFor)).thenReturn(2);

        // when
        service.page(null, null, 20);
        var actual = service.unreadCount();
        service.markRead(7L);
        service.markAllRead();

        // then
        assertThat(actual).isEqualTo(2);
        verify(reads).page(actingFor, null, null, 20);
        verify(reads).markRead(actingFor, 7L, NOW);
        verify(reads).markAllRead(actingFor, NOW);
    }

    @Test
    void markReadStampsTheClockOnTheSignedInMembersEntry() {

        when(currentUser.userId()).thenReturn(USER_ID);

        service.markRead(7L);

        verify(reads).markRead(MEMBER, 7L, NOW);
    }

    @Test
    void markAllReadStampsTheClockForTheSignedInMember() {

        when(currentUser.userId()).thenReturn(USER_ID);

        service.markAllRead();

        verify(reads).markAllRead(MEMBER, NOW);
    }
}
