package dev.axonia.communication;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = NotificationCentreController.class)
class NotificationCentreControllerTest extends ControllerTestSupport {

    @MockitoBean
    private NotificationCentreService notificationCentreService;

    private static NotificationEntry anEntry() {
        return NotificationEntry.builder().id(1L).headline("Frontend Dojo")
                .sentAt(Instant.parse("2026-08-01T09:00:00Z")).read(false).build();
    }

    @Test
    void thePanelReturnsAPageForTheSignedInMember() {

        // given
        when(notificationCentreService.page(null, null, 20))
                .thenReturn(new NotificationPage(List.of(anEntry()), false, null, null));

        // when
        var actual = mvc.get().uri("/axonia/me/notifications");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.entries[0].headline")
                .isEqualTo("Frontend Dojo");
        actual.assertThat().bodyJson().extractingPath("$.hasMore").isEqualTo(false);
    }

    /**
     * The cursor the next request pages back from travels in the response, not in the client's head. A client deriving
     * it from the last entry it can see has nothing to derive it from when every row of a window was dropped as an
     * unrecognized type, and stops paging there for good - so these two fields being on the wire is the whole fix.
     */
    @Test
    void aPageWithMoreBehindItCarriesTheCursorToResumeFrom() {

        // given
        when(notificationCentreService.page(null, null, 20))
                .thenReturn(new NotificationPage(List.of(), true, Instant.parse("2026-08-01T10:00:00Z"), 7L));

        // when
        var actual = mvc.get().uri("/axonia/me/notifications");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.nextBefore")
                .isEqualTo("2026-08-01T10:00:00Z");
        actual.assertThat().bodyJson().extractingPath("$.nextBeforeId").isEqualTo(7);
    }

    @Test
    void theCursorIsPassedThrough() {

        // given
        when(notificationCentreService.page(Instant.parse("2026-08-01T10:00:00Z"), 7L, 20))
                .thenReturn(new NotificationPage(List.of(), false, null, null));

        // when
        var actual = mvc.get().uri("/axonia/me/notifications").queryParam("before", "2026-08-01T10:00:00Z")
                .queryParam("beforeId", "7");

        // then
        actual.assertThat().hasStatus2xxSuccessful();
        verify(notificationCentreService).page(Instant.parse("2026-08-01T10:00:00Z"), 7L, 20);
    }

    @Test
    void aMalformedBeforeIsARequest400RatherThanAServerError() {

        // when
        var actual = mvc.get().uri("/axonia/me/notifications").queryParam("before", "not-a-date");

        // then
        actual.assertThat().hasStatus(400);
    }

    @Test
    void theBadgeReturnsACount() {

        // given
        when(notificationCentreService.unreadCount()).thenReturn(3);

        // when
        var actual = mvc.get().uri("/axonia/me/notifications/unread-count");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.count").isEqualTo(3);
    }

    @Test
    void markingOneReadReturnsNoContent() {

        // when
        var actual = mvc.post().uri("/axonia/me/notifications/{id}/read", 7L);

        // then
        actual.assertThat().hasStatus(204);
        verify(notificationCentreService).markRead(7L);
    }

    @Test
    void markingAllReadReturnsNoContent() {

        // when
        var actual = mvc.post().uri("/axonia/me/notifications/read-all");

        // then
        actual.assertThat().hasStatus(204);
        verify(notificationCentreService).markAllRead();
    }
}
