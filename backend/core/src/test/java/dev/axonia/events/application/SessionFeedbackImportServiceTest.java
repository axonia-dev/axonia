package dev.axonia.events.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.events.api.FeedbackImportRequest;
import dev.axonia.events.api.FeedbackImportRequest.Answer;
import dev.axonia.events.api.FeedbackImportResult;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.FeedbackImportStore;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SessionFeedbackStore;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.FeedbackImportRecord;
import dev.axonia.events.domain.ImportedAnswer;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class SessionFeedbackImportServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
    private static final String ORG = "default";
    private static final long SESSION_ID = 42L;

    @Mock
    private SessionStore sessionStore;
    @Mock
    private SessionFeedbackStore feedbackStore;
    @Mock
    private FeedbackImportStore importStore;
    @Mock
    private SessionAuthorizer sessionAuthorizer;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private OrganizationContext organizationContext;

    private SessionFeedbackImportService sut;

    @BeforeEach
    void wire() {

        sut = new SessionFeedbackImportService(sessionStore, feedbackStore, importStore, sessionAuthorizer, currentUser,
                organizationContext, () -> Duration.ofDays(30), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static SessionRecord session(String status, Instant scheduledAt, Instant closedAt) {

        return SessionRecord.builder().id(SESSION_ID).communityId(1L).status(status).scheduledAt(scheduledAt)
                .closedAt(closedAt).build();
    }

    /** Held 20 days ago and closed the next day: its window closed 12 days ago. */
    private static SessionRecord closedWindow() {

        return session("HELD", NOW.minus(Duration.ofDays(20)), NOW.minus(Duration.ofDays(19)));
    }

    private void stored(SessionRecord session) {

        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn("orga");
        when(sessionStore.findByOrganizationIdAndId(ORG, SESSION_ID)).thenReturn(Optional.of(session));
    }

    private static FeedbackImportRequest request(int scale, String source, Answer... answers) {

        return FeedbackImportRequest.builder().scale(scale).source(source).answers(List.of(answers)).build();
    }

    private FeedbackImportRecord replaced() {

        var captor = ArgumentCaptor.forClass(FeedbackImportRecord.class);
        verify(importStore).replace(captor.capture());
        return captor.getValue();
    }

    @Test
    void import_storesAndReportsRevealedAtThree() {

        // given
        stored(closedWindow());

        // when
        var result = sut.importFeedback(SESSION_ID,
                request(5, "  Microsoft Forms ", new Answer(5, 4), new Answer(3, null), new Answer(null, 2)));

        // then
        assertThat(result).isEqualTo(new FeedbackImportResult(SESSION_ID, 2, 2, true));
        var record = replaced();
        assertThat(record.getOrganizationId()).isEqualTo(ORG);
        assertThat(record.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(record.getScale()).isEqualTo(5);
        assertThat(record.getSource()).isEqualTo("Microsoft Forms");
        assertThat(record.getCreatedAt()).isEqualTo(NOW);
        assertThat(record.getAnswers()).containsExactly(new ImportedAnswer(5, 4), new ImportedAnswer(3, null),
                new ImportedAnswer(null, 2));
    }

    @Test
    void import_belowThreeIsNotRevealed() {

        // given
        stored(closedWindow());

        // when
        var result = sut.importFeedback(SESSION_ID, request(10, "Google Forms", new Answer(9, 8), new Answer(7, 6)));

        // then
        assertThat(result).isEqualTo(new FeedbackImportResult(SESSION_ID, 2, 2, false));
    }

    @Test
    void import_refusesAnUnknownSession() {

        // given
        when(organizationContext.organizationId()).thenReturn(ORG);
        when(sessionStore.findByOrganizationIdAndId(ORG, SESSION_ID)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.importFeedback(SESSION_ID, request(5, "Forms", new Answer(5, 5))))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(importStore);
    }

    @Test
    void import_refusesANonOrganizer() {

        // given
        var session = closedWindow();
        stored(session);
        doThrow(new ForbiddenException("Only an organizer may do this")).when(sessionAuthorizer)
                .requireOrganizer(session, "orga");

        // when / then
        assertThatThrownBy(() -> sut.importFeedback(SESSION_ID, request(5, "Forms", new Answer(5, 5))))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(importStore);
    }

    @Test
    void import_refusesANotHeldSession() {

        // given
        stored(session("SCHEDULED", NOW.minus(Duration.ofDays(20)), null));

        // when / then
        assertThatThrownBy(() -> sut.importFeedback(SESSION_ID, request(5, "Forms", new Answer(5, 5))))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("SESSION_NOT_HELD");
        verifyNoInteractions(importStore);
    }

    @Test
    void import_refusesAnOpenWindow() {

        // given
        stored(session("HELD", NOW.minus(Duration.ofDays(3)), NOW.minus(Duration.ofDays(2))));

        // when / then
        assertThatThrownBy(() -> sut.importFeedback(SESSION_ID, request(5, "Forms", new Answer(5, 5))))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("FEEDBACK_WINDOW_OPEN");
        verifyNoInteractions(importStore);
    }

    @Test
    void import_refusesASessionWithMemberAnswers() {

        // given
        stored(closedWindow());
        when(feedbackStore.anyForSession(ORG, SESSION_ID)).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> sut.importFeedback(SESSION_ID, request(5, "Forms", new Answer(5, 5))))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("FEEDBACK_HAS_ANSWERS");
        verifyNoInteractions(importStore);
    }

    @Test
    void import_acceptsAClosedWindowWithNoAnswers() {

        // given
        stored(closedWindow());
        when(feedbackStore.anyForSession(ORG, SESSION_ID)).thenReturn(false);

        // when
        var result = sut.importFeedback(SESSION_ID, request(5, "Forms", new Answer(5, 5)));

        // then
        assertThat(result).isEqualTo(new FeedbackImportResult(SESSION_ID, 1, 1, false));
        assertThat(replaced().getAnswers()).containsExactly(new ImportedAnswer(5, 5));
    }

    /** Closed 31 days after it was held: no window ever opened, so the import is history. */
    @Test
    void import_acceptsALateClosedSession() {

        // given
        stored(session("HELD", NOW.minus(Duration.ofDays(32)), NOW.minus(Duration.ofDays(1))));

        // when
        var result = sut.importFeedback(SESSION_ID,
                request(5, "Forms", new Answer(4, 4), new Answer(5, 3), new Answer(2, 5)));

        // then
        assertThat(result).isEqualTo(new FeedbackImportResult(SESSION_ID, 3, 3, true));
        assertThat(replaced().getAnswers()).hasSize(3);
    }

    @Test
    void import_refusesAScaleOtherThanFiveOrTen() {

        // given
        stored(closedWindow());

        // when / then
        assertThatThrownBy(() -> sut.importFeedback(SESSION_ID, request(7, "Forms", new Answer(5, 5))))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("FEEDBACK_SCALE_UNSUPPORTED");
        verifyNoInteractions(importStore);
    }

    @Test
    void import_refusesAValueOutsideTheScale() {

        // given
        stored(closedWindow());

        // when / then
        assertThatThrownBy(
                () -> sut.importFeedback(SESSION_ID, request(5, "Forms", new Answer(5, 5), new Answer(4, 6))))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("FEEDBACK_SCORE_OUT_OF_SCALE");
        verifyNoInteractions(importStore);
    }

    @Test
    void import_refusesAnAnswerWithNeitherValue() {

        // given
        stored(closedWindow());

        // when / then
        assertThatThrownBy(() -> sut.importFeedback(SESSION_ID, request(5, "Forms", new Answer(null, null))))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("FEEDBACK_ANSWER_EMPTY");
        verifyNoInteractions(importStore);
    }

    @Test
    void import_refusesANullAnswer() {

        // given
        stored(closedWindow());
        var answers = new ArrayList<Answer>(Arrays.asList(new Answer(5, 5), null));

        // when / then
        assertThatThrownBy(() -> sut.importFeedback(SESSION_ID,
                FeedbackImportRequest.builder().scale(5).source("Forms").answers(answers).build()))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("FEEDBACK_ANSWER_EMPTY");
        verifyNoInteractions(importStore);
    }

    @Test
    void import_refusesABlankSource() {

        // given
        stored(closedWindow());

        // when / then
        assertThatThrownBy(() -> sut.importFeedback(SESSION_ID, request(5, "   ", new Answer(5, 5))))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("INVALID_FEEDBACK_SOURCE");
        verifyNoInteractions(importStore);
    }

    @Test
    void import_refusesAMissingSource() {

        // given
        stored(closedWindow());

        // when / then
        assertThatThrownBy(() -> sut.importFeedback(SESSION_ID, request(5, null, new Answer(5, 5))))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("INVALID_FEEDBACK_SOURCE");
        verifyNoInteractions(importStore);
    }

    @Test
    void import_refusesASourceWiderThanItsColumn() {

        // given
        stored(closedWindow());
        var source = "x".repeat(SessionFeedbackImportService.MAX_SOURCE_LENGTH + 1);

        // when / then
        assertThatThrownBy(() -> sut.importFeedback(SESSION_ID, request(5, source, new Answer(5, 5))))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("INVALID_FEEDBACK_SOURCE");
        verifyNoInteractions(importStore);
    }

    @Test
    void import_acceptsASourceAsWideAsItsColumn() {

        // given
        stored(closedWindow());
        var source = "x".repeat(SessionFeedbackImportService.MAX_SOURCE_LENGTH);

        // when
        sut.importFeedback(SESSION_ID, request(5, source, new Answer(5, 5)));

        // then
        assertThat(replaced().getSource()).isEqualTo(source);
    }

    @Test
    void import_acceptsAsManyAnswersAsTheCap() {

        // given
        stored(closedWindow());
        var answers = Collections.nCopies(SessionFeedbackImportService.MAX_ANSWERS, new Answer(5, 5));

        // when
        sut.importFeedback(SESSION_ID,
                FeedbackImportRequest.builder().scale(5).source("Forms").answers(answers).build());

        // then
        assertThat(replaced().getAnswers()).hasSize(SessionFeedbackImportService.MAX_ANSWERS);
    }

    @Test
    void import_refusesMoreAnswersThanTheCap() {

        // given
        stored(closedWindow());
        var answers = Collections.nCopies(SessionFeedbackImportService.MAX_ANSWERS + 1, new Answer(5, 5));

        // when / then
        assertThatThrownBy(() -> sut.importFeedback(SESSION_ID,
                FeedbackImportRequest.builder().scale(5).source("Forms").answers(answers).build()))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("FEEDBACK_TOO_MANY_ANSWERS");
        verifyNoInteractions(importStore);
    }

    @Test
    void import_refusesAMissingList() {

        // given
        stored(closedWindow());

        // when / then
        assertThatThrownBy(
                () -> sut.importFeedback(SESSION_ID, FeedbackImportRequest.builder().scale(5).source("Forms").build()))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("FEEDBACK_ANSWERS_REQUIRED");
        verifyNoInteractions(importStore);
    }

    /** Removing needs neither a scale nor a source: there is nothing left to describe. */
    @Test
    void import_emptyListRemovesTheImport() {

        // given
        stored(closedWindow());

        // when
        var result = sut.importFeedback(SESSION_ID, request(0, null));

        // then
        assertThat(result).isEqualTo(new FeedbackImportResult(SESSION_ID, 0, 0, false));
        var record = replaced();
        assertThat(record.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(record.getAnswers()).isEmpty();
    }
}
