package dev.axonia.bookmarks.adapter.in.web;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.bookmarks.application.BookmarkService;
import dev.axonia.bookmarks.application.SavedSessionView;
import dev.axonia.bookmarks.domain.SaveState;
import dev.axonia.bookmarks.domain.SaveableRef;
import dev.axonia.bookmarks.domain.SavedSession;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = BookmarkController.class)
class BookmarkControllerTest extends ControllerTestSupport {

    @MockitoBean
    private BookmarkService bookmarkService;

    @Test
    void saveArtifact() {

        // given
        when(bookmarkService.save(SaveableRef.artifact(100L)))
                .thenReturn(SaveState.builder().viewerHasSaved(true).viewerHelped(null).build());

        // when
        var actual = mvc.post().uri("/axonia/artifacts/{id}/save", 100L);

        // then
        actual.assertThat().hasStatus(201).bodyJson().extractingPath("$.viewerHasSaved").isEqualTo(true);
    }

    @Test
    void unsaveArtifact() {

        // when
        var actual = mvc.delete().uri("/axonia/artifacts/{id}/save", 100L);

        // then
        actual.assertThat().hasStatus(204);
        verify(bookmarkService).unsave(SaveableRef.artifact(100L));
    }

    @Test
    void artifactState() {

        // given
        when(bookmarkService.saveState(SaveableRef.artifact(100L)))
                .thenReturn(SaveState.builder().viewerHasSaved(true).viewerHelped(true).build());

        // when
        var actual = mvc.get().uri("/axonia/artifacts/{id}/save", 100L);

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.viewerHelped").isEqualTo(true);
    }

    @Test
    void helpful() {

        // given
        when(bookmarkService.markHelpful(eq(100L), eq(true)))
                .thenReturn(SaveState.builder().viewerHasSaved(true).viewerHelped(true).build());

        // when
        var actual = mvc.put().uri("/axonia/artifacts/{id}/helpful", 100L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"helped\":true}");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.viewerHelped").isEqualTo(true);
    }

    @Test
    void helpful_nullClearsTheMarkToNeutral() {

        // given
        when(bookmarkService.markHelpful(eq(100L), isNull()))
                .thenReturn(SaveState.builder().viewerHasSaved(true).viewerHelped(null).build());

        // when
        var actual = mvc.put().uri("/axonia/artifacts/{id}/helpful", 100L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"helped\":null}");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.viewerHelped").isEqualTo(null);
    }

    @Test
    void saving_a_session_posts_to_the_session_save_route() {

        // given
        when(bookmarkService.save(SaveableRef.session(500L)))
                .thenReturn(SaveState.builder().viewerHasSaved(true).build());

        // when
        var actual = mvc.post().uri("/axonia/sessions/{id}/save", 500L);

        // then
        actual.assertThat().hasStatus(201).bodyJson().extractingPath("$.viewerHasSaved").isEqualTo(true);
        verify(bookmarkService).save(SaveableRef.session(500L));
    }

    @Test
    void unsaveSession() {

        // when
        var actual = mvc.delete().uri("/axonia/sessions/{id}/save", 500L);

        // then
        actual.assertThat().hasStatus(204);
        verify(bookmarkService).unsave(SaveableRef.session(500L));
    }

    @Test
    void sessionState() {

        // given
        when(bookmarkService.saveState(SaveableRef.session(500L)))
                .thenReturn(SaveState.builder().viewerHasSaved(false).viewerHelped(null).build());

        // when
        var actual = mvc.get().uri("/axonia/sessions/{id}/save", 500L);

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$.viewerHasSaved").isEqualTo(false);
    }

    @Test
    void mySaved_serializes_the_kind_discriminator() {

        // given
        when(bookmarkService.mySaved()).thenReturn(
                List.of(SavedSessionView.builder().session(SavedSession.builder().id(500L).title("Retro").build())
                        .savedAt(Instant.parse("2026-07-01T00:00:00Z")).build()));

        // when
        var actual = mvc.get().uri("/axonia/me/saved");

        // then
        actual.assertThat().hasStatusOk().bodyJson().extractingPath("$[0].kind").isEqualTo("session");
    }
}
