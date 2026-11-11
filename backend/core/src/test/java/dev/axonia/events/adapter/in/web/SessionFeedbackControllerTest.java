package dev.axonia.events.adapter.in.web;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.events.api.FeedbackAnswer;
import dev.axonia.events.api.SessionFeedbackView;
import dev.axonia.events.api.SessionFeedbackView.Result;
import dev.axonia.events.api.SessionFeedbackView.ResultState;
import dev.axonia.events.api.SessionFeedbackView.Role;
import dev.axonia.events.api.SessionFeedbackView.Window;
import dev.axonia.events.application.SessionFeedbackService;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = SessionFeedbackController.class)
class SessionFeedbackControllerTest extends ControllerTestSupport {

    private static final SessionFeedbackView VIEW = SessionFeedbackView.builder().window(Window.CLOSED)
            .closesAt(Instant.parse("2026-11-13T12:00:00Z")).role(Role.PRESENTER).mine(null)
            .result(Result.builder().state(ResultState.REVEALED).answers(9).enjoyed(9.1).learned(7.4).build()).build();

    @MockitoBean
    private SessionFeedbackService sessionFeedbackService;

    @Test
    void view_returnsTheWireShape() {

        // given
        when(sessionFeedbackService.view(9L)).thenReturn(VIEW);

        // when
        var actual = mvc.get().uri("/axonia/sessions/{id}/feedback", 9L).exchange();

        // then
        actual.assertThat().hasStatus(200);
        actual.assertThat().bodyJson().isLenientlyEqualTo("""
                {"window":"CLOSED","closesAt":"2026-11-13T12:00:00Z","role":"PRESENTER","mine":null,
                 "result":{"state":"REVEALED","answers":9,"enjoyed":9.1,"learned":7.4,"importedFrom":null}}
                """);
        actual.assertThat().bodyJson().hasPath("$.mine");
        actual.assertThat().bodyJson().hasPath("$.result.importedFrom");
    }

    @Test
    void give_returns200WithTheView() {

        // given
        var mine = SessionFeedbackView.builder().window(Window.OPEN).role(Role.ATTENDEE).mine(new FeedbackAnswer(9, 7))
                .build();
        when(sessionFeedbackService.give(9L, new FeedbackAnswer(9, 7))).thenReturn(mine);

        // when
        var actual = mvc.put().uri("/axonia/sessions/{id}/feedback/me", 9L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enjoyed\":9,\"learned\":7}").exchange();

        // then
        actual.assertThat().hasStatus(200);
        actual.assertThat().bodyJson().extractingPath("$.mine.enjoyed").isEqualTo(9);
        actual.assertThat().bodyJson().extractingPath("$.mine.learned").isEqualTo(7);
        actual.assertThat().bodyJson().extractingPath("$.role").isEqualTo("ATTENDEE");
    }

    @Test
    void give_enjoyedBelowOneIs400() {

        // when
        var actual = mvc.put().uri("/axonia/sessions/{id}/feedback/me", 9L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enjoyed\":0,\"learned\":7}").exchange();

        // then
        actual.assertThat().hasStatus(400);
        verify(sessionFeedbackService, never()).give(eq(9L), any());
    }

    @Test
    void give_learnedAboveTenIs400() {

        // when
        var actual = mvc.put().uri("/axonia/sessions/{id}/feedback/me", 9L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enjoyed\":9,\"learned\":11}").exchange();

        // then
        actual.assertThat().hasStatus(400);
        verify(sessionFeedbackService, never()).give(eq(9L), any());
    }

    @Test
    void give_notEligibleIs403WithCode() {

        // given
        when(sessionFeedbackService.give(9L, new FeedbackAnswer(9, 7))).thenThrow(
                new ForbiddenException("FEEDBACK_NOT_ELIGIBLE", "Only a member or a presenter may give feedback"));

        // when
        var actual = mvc.put().uri("/axonia/sessions/{id}/feedback/me", 9L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enjoyed\":9,\"learned\":7}").exchange();

        // then
        actual.assertThat().hasStatus(403);
        actual.assertThat().bodyJson().extractingPath("$.code").isEqualTo("FEEDBACK_NOT_ELIGIBLE");
    }

    @Test
    void give_delegatedSessionIs403WithTheOwnSignInCode() {

        // given
        when(sessionFeedbackService.give(9L, new FeedbackAnswer(9, 7))).thenThrow(new OwnSignInRequiredException());

        // when
        var actual = mvc.put().uri("/axonia/sessions/{id}/feedback/me", 9L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enjoyed\":9,\"learned\":7}").exchange();

        // then
        actual.assertThat().hasStatus(403);
        actual.assertThat().bodyJson().extractingPath("$.code").isEqualTo("REQUIRES_OWN_SESSION");
    }
}
