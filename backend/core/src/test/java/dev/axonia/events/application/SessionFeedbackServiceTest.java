package dev.axonia.events.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import dev.axonia.events.api.FeedbackAnswer;
import dev.axonia.events.api.SessionFeedbackView.Result;
import dev.axonia.events.api.SessionFeedbackView.ResultState;
import dev.axonia.events.api.SessionFeedbackView.Role;
import dev.axonia.events.api.SessionFeedbackView.Window;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.FeedbackImportStore;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SessionFeedbackStore;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.application.port.out.WrapUpWindow;
import dev.axonia.events.domain.FeedbackImportRecord;
import dev.axonia.events.domain.FeedbackRecord;
import dev.axonia.events.domain.FeedbackTally;
import dev.axonia.events.domain.SectionRecord;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.events.domain.SessionRecordMother;
import dev.axonia.events.domain.SlotPresenter;
import dev.axonia.events.domain.SlotRecord;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class SessionFeedbackServiceTest {

    private static final String ORG = "default";
    private static final String ALICE = "alice";
    private static final long SESSION = 9L;
    private static final long COMMUNITY = 1L;
    private static final Instant SCHEDULED = Instant.parse("2026-11-10T18:00:00Z");
    private static final Instant CLOSED_AT = Instant.parse("2026-11-10T20:00:00Z");
    private static final Instant CLOSES_AT = CLOSED_AT.plus(Duration.ofDays(7));
    private static final Instant INSIDE = CLOSED_AT.plus(Duration.ofDays(1));
    private static final Instant AFTER = CLOSES_AT.plusSeconds(1);

    @Mock
    private SessionStore sessionStore;

    @Mock
    private SessionFeedbackStore sessionFeedbackStore;

    @Mock
    private FeedbackImportStore feedbackImportStore;

    @Mock
    private CommunityAccess communityAccess;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private OrganizationContext organizationContext;

    private SessionFeedbackService sut;

    @BeforeEach
    void seedSeams() {

        lenient().when(organizationContext.organizationId()).thenReturn(ORG);
        lenient().when(currentUser.userId()).thenReturn(ALICE);
        lenient().when(communityAccess.isMember(COMMUNITY, ALICE)).thenReturn(true);
        lenient().when(feedbackImportStore.find(ORG, SESSION)).thenReturn(Optional.empty());
        lenient().when(sessionFeedbackStore.findMine(ORG, SESSION, ALICE)).thenReturn(Optional.empty());
        at(INSIDE);
    }

    @Test
    void view_attendeeWithOpenWindowAndNoAnswer() {

        // given
        held(heldSession());

        // when
        var actual = sut.view(SESSION);

        // then
        assertThat(actual.role()).isEqualTo(Role.ATTENDEE);
        assertThat(actual.window()).isEqualTo(Window.OPEN);
        assertThat(actual.closesAt()).isEqualTo(CLOSES_AT);
        assertThat(actual.mine()).isNull();
        assertThat(actual.result()).isNull();
    }

    @Test
    void view_showsTheMembersOwnAnswer() {

        // given
        held(heldSession());
        when(sessionFeedbackStore.findMine(ORG, SESSION, ALICE)).thenReturn(Optional.of(answered(9, 7)));

        // when
        var actual = sut.view(SESSION);

        // then
        assertThat(actual.mine()).isEqualTo(new FeedbackAnswer(9, 7));
    }

    @Test
    void view_presenterWhoIsAlsoOrganizer_isPresenterAndSeesTheResult() {

        // given
        held(presentedBy(ALICE));
        lenient().when(communityAccess.isOrganizer(COMMUNITY, ALICE)).thenReturn(true);
        lenient().when(communityAccess.canViewHealth(COMMUNITY, ALICE)).thenReturn(true);

        // when
        var actual = sut.view(SESSION);

        // then
        assertThat(actual.role()).isEqualTo(Role.PRESENTER);
        assertThat(actual.result().state()).isEqualTo(ResultState.PENDING);
    }

    @Test
    void view_presenterOfAnotherSlotIsStillAnAttendee() {

        // given
        held(presentedBy("bob"));

        // when
        var actual = sut.view(SESSION);

        // then
        assertThat(actual.role()).isEqualTo(Role.ATTENDEE);
    }

    @Test
    void view_sessionWithoutSectionsOrSlotsHasNoPresenter() {

        // given
        var noSlots = SectionRecord.builder().slots(null).build();
        var noPresenters = SectionRecord.builder().slots(List.of(SlotRecord.builder().presenters(null).build()))
                .build();
        held(heldSession().toBuilder().sections(List.of(noSlots, noPresenters)).build());

        // when
        var actual = sut.view(SESSION);

        // then
        assertThat(actual.role()).isEqualTo(Role.ATTENDEE);
    }

    @Test
    void view_nullSectionsHaveNoPresenter() {

        // given
        held(heldSession().toBuilder().sections(null).build());

        // when
        var actual = sut.view(SESSION);

        // then
        assertThat(actual.role()).isEqualTo(Role.ATTENDEE);
    }

    @Test
    void view_resultPendingWhileOpen() {

        // given
        held(presentedBy(ALICE));

        // when
        var actual = sut.view(SESSION);

        // then
        assertThat(actual.result()).isEqualTo(Result.builder().state(ResultState.PENDING).answers(0).build());
        verify(sessionFeedbackStore, never()).tally(any(), anyLong());
    }

    @Test
    void view_resultTooFewAfterCloseWithTwo() {

        // given
        at(AFTER);
        held(presentedBy(ALICE));
        when(sessionFeedbackStore.tally(ORG, SESSION)).thenReturn(new FeedbackTally(2, 2, 18, 2, 14));

        // when
        var actual = sut.view(SESSION);

        // then
        assertThat(actual.window()).isEqualTo(Window.CLOSED);
        assertThat(actual.result()).isEqualTo(Result.builder().state(ResultState.TOO_FEW).answers(0).build());
    }

    @Test
    void view_resultRevealedAfterCloseWithThree() {

        // given
        at(AFTER);
        held(presentedBy(ALICE));
        when(sessionFeedbackStore.tally(ORG, SESSION)).thenReturn(new FeedbackTally(3, 3, 26, 3, 22));

        // when
        var actual = sut.view(SESSION);

        // then
        assertThat(actual.result())
                .isEqualTo(Result.builder().state(ResultState.REVEALED).answers(3).enjoyed(8.7).learned(7.3).build());
    }

    @Test
    void view_importedSessionRevealedAtOnceWithItsSource() {

        // given
        held(presentedBy(ALICE));
        imported();
        when(feedbackImportStore.tally(ORG, SESSION)).thenReturn(new FeedbackTally(4, 4, 36, 2, 15));

        // when
        var actual = sut.view(SESSION);

        // then
        assertThat(actual.result()).isEqualTo(Result.builder().state(ResultState.REVEALED).answers(4).enjoyed(9.0)
                .learned(7.5).importedFrom("Microsoft Forms").build());
    }

    @Test
    void view_importedSessionWithTooFewAnswersKeepsItsSource() {

        // given
        held(presentedBy(ALICE));
        imported();
        when(feedbackImportStore.tally(ORG, SESSION)).thenReturn(new FeedbackTally(2, 2, 18, 0, 0));

        // when
        var actual = sut.view(SESSION);

        // then
        assertThat(actual.result()).isEqualTo(
                Result.builder().state(ResultState.TOO_FEW).answers(0).importedFrom("Microsoft Forms").build());
    }

    @Test
    void view_noWindowAndNoImportShowsNoResult() {

        // given
        held(presentedBy(ALICE).toBuilder().status("SCHEDULED").closedAt(null).build());

        // when
        var actual = sut.view(SESSION);

        // then
        assertThat(actual.window()).isEqualTo(Window.NONE);
        assertThat(actual.closesAt()).isNull();
        assertThat(actual.result()).isNull();
    }

    @Test
    void view_canViewHealthMemberSeesTheResult() {

        // given
        held(heldSession());
        when(communityAccess.canViewHealth(COMMUNITY, ALICE)).thenReturn(true);

        // when
        var actual = sut.view(SESSION);

        // then
        assertThat(actual.role()).isEqualTo(Role.ATTENDEE);
        assertThat(actual.result().state()).isEqualTo(ResultState.PENDING);
    }

    @Test
    void view_plainMemberSeesNoResult() {

        // given
        held(heldSession());
        when(communityAccess.canViewHealth(COMMUNITY, ALICE)).thenReturn(false);

        // when
        var actual = sut.view(SESSION);

        // then
        assertThat(actual.result()).isNull();
    }

    @Test
    void view_nonMemberIsOther() {

        // given
        held(heldSession());
        when(communityAccess.isMember(COMMUNITY, ALICE)).thenReturn(false);

        // when
        var actual = sut.view(SESSION);

        // then
        assertThat(actual.role()).isEqualTo(Role.OTHER);
        assertThat(actual.result()).isNull();
    }

    @Test
    void view_nonMemberPresenterIsPresenterAndSeesTheResult() {

        // given
        at(AFTER);
        held(presentedBy(ALICE));
        lenient().when(communityAccess.isMember(COMMUNITY, ALICE)).thenReturn(false);
        when(sessionFeedbackStore.tally(ORG, SESSION)).thenReturn(new FeedbackTally(2, 2, 18, 2, 14));

        // when
        var actual = sut.view(SESSION);

        // then
        assertThat(actual.role()).isEqualTo(Role.PRESENTER);
        assertThat(actual.result()).isEqualTo(Result.builder().state(ResultState.TOO_FEW).answers(0).build());
    }

    @Test
    void view_unknownSessionIsNotFound() {

        // given
        when(sessionStore.findByOrganizationIdAndId(ORG, SESSION)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.view(SESSION)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void give_storesANewAnswer() {

        // given
        held(heldSession());
        var saved = ArgumentCaptor.forClass(FeedbackRecord.class);

        // when
        sut.give(SESSION, new FeedbackAnswer(9, 7));

        // then
        verify(sessionFeedbackStore).save(saved.capture());
        assertThat(saved.getValue()).usingRecursiveComparison().isEqualTo(FeedbackRecord.builder().organizationId(ORG)
                .sessionId(SESSION).userId(ALICE).enjoyed(9).learned(7).build());
    }

    @Test
    void give_replacesTheMembersAnswer() {

        // given
        held(heldSession());
        var existing = answered(4, 5);
        when(sessionFeedbackStore.findMine(ORG, SESSION, ALICE)).thenReturn(Optional.of(existing));
        var saved = ArgumentCaptor.forClass(FeedbackRecord.class);

        // when
        sut.give(SESSION, new FeedbackAnswer(9, 7));

        // then
        verify(sessionFeedbackStore, times(1)).save(saved.capture());
        assertThat(saved.getValue()).usingRecursiveComparison()
                .isEqualTo(existing.toBuilder().enjoyed(9).learned(7).build());
    }

    @Test
    void give_returnsTheViewWithTheNewAnswer() {

        // given
        held(heldSession());
        when(sessionFeedbackStore.findMine(ORG, SESSION, ALICE)).thenReturn(Optional.empty(),
                Optional.of(answered(9, 7)));

        // when
        var actual = sut.give(SESSION, new FeedbackAnswer(9, 7));

        // then
        assertThat(actual.mine()).isEqualTo(new FeedbackAnswer(9, 7));
        assertThat(actual.window()).isEqualTo(Window.OPEN);
    }

    @Test
    void give_aRaceOnTheUniqueConstraintRereadsAndReplaces() {

        // given
        held(heldSession());
        var winner = answered(4, 5);
        when(sessionFeedbackStore.findMine(ORG, SESSION, ALICE)).thenReturn(Optional.empty(), Optional.of(winner));
        when(sessionFeedbackStore.save(any())).thenThrow(new DataIntegrityViolationException("unique"))
                .thenAnswer(inv -> inv.getArgument(0));
        var saved = ArgumentCaptor.forClass(FeedbackRecord.class);

        // when
        sut.give(SESSION, new FeedbackAnswer(9, 7));

        // then
        verify(sessionFeedbackStore, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1)).usingRecursiveComparison()
                .isEqualTo(winner.toBuilder().enjoyed(9).learned(7).build());
    }

    @Test
    void give_aRaceOnTheVersionRereadsAndReplaces() {

        // given
        held(heldSession());
        var stale = answered(4, 5);
        var winner = stale.toBuilder().enjoyed(6).version(3L).build();
        when(sessionFeedbackStore.findMine(ORG, SESSION, ALICE)).thenReturn(Optional.of(stale), Optional.of(winner));
        when(sessionFeedbackStore.save(any())).thenThrow(new OptimisticLockingFailureException("version"))
                .thenAnswer(inv -> inv.getArgument(0));
        var saved = ArgumentCaptor.forClass(FeedbackRecord.class);

        // when
        sut.give(SESSION, new FeedbackAnswer(9, 7));

        // then
        verify(sessionFeedbackStore, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1)).usingRecursiveComparison()
                .isEqualTo(winner.toBuilder().enjoyed(9).learned(7).build());
    }

    @Test
    void give_refusesADelegatedSession() {

        // given
        held(heldSession());
        when(currentUser.delegated()).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> sut.give(SESSION, new FeedbackAnswer(9, 7)))
                .isInstanceOf(OwnSignInRequiredException.class);
        verify(sessionFeedbackStore, never()).save(any());
    }

    @Test
    void view_withholdsTheMembersAnswerFromADelegatedSession() {

        // given
        held(heldSession());
        when(currentUser.delegated()).thenReturn(true);

        // when
        var actual = sut.view(SESSION);

        // then
        assertThat(actual.mine()).isNull();
        assertThat(actual.role()).isEqualTo(Role.ATTENDEE);
        verify(sessionFeedbackStore, never()).findMine(any(), anyLong(), any());
    }

    @Test
    void give_refusesAPresenter() {

        // given
        held(presentedBy(ALICE));

        // when / then
        assertThatThrownBy(() -> sut.give(SESSION, new FeedbackAnswer(9, 7))).isInstanceOfSatisfying(
                ForbiddenException.class, ex -> assertThat(ex.getCode()).isEqualTo("FEEDBACK_NOT_ELIGIBLE"));
        verify(sessionFeedbackStore, never()).save(any());
    }

    @Test
    void give_refusesANonMemberPresenter() {

        // given
        held(presentedBy(ALICE));
        lenient().when(communityAccess.isMember(COMMUNITY, ALICE)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> sut.give(SESSION, new FeedbackAnswer(9, 7))).isInstanceOfSatisfying(
                ForbiddenException.class, ex -> assertThat(ex.getCode()).isEqualTo("FEEDBACK_NOT_ELIGIBLE"));
        verify(sessionFeedbackStore, never()).save(any());
    }

    @Test
    void give_refusesANonMember() {

        // given
        held(heldSession());
        when(communityAccess.isMember(COMMUNITY, ALICE)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> sut.give(SESSION, new FeedbackAnswer(9, 7))).isInstanceOfSatisfying(
                ForbiddenException.class, ex -> assertThat(ex.getCode()).isEqualTo("FEEDBACK_NOT_ELIGIBLE"));
        verify(sessionFeedbackStore, never()).save(any());
    }

    @Test
    void give_refusesAClosedWindow() {

        // given
        at(AFTER);
        held(heldSession());

        // when / then
        assertThatThrownBy(() -> sut.give(SESSION, new FeedbackAnswer(9, 7))).isInstanceOfSatisfying(
                ConflictException.class, ex -> assertThat(ex.getCode()).isEqualTo("FEEDBACK_WINDOW_CLOSED"));
        verify(sessionFeedbackStore, never()).save(any());
    }

    @Test
    void give_refusesWithNoWindow() {

        // given
        held(heldSession().toBuilder().closedAt(SCHEDULED.plus(Duration.ofDays(31))).build());

        // when / then
        assertThatThrownBy(() -> sut.give(SESSION, new FeedbackAnswer(9, 7))).isInstanceOfSatisfying(
                ConflictException.class, ex -> assertThat(ex.getCode()).isEqualTo("FEEDBACK_WINDOW_CLOSED"));
        verify(sessionFeedbackStore, never()).save(any());
    }

    @Test
    void give_refusesAnImportedSession() {

        // given
        held(heldSession());
        imported();

        // when / then
        assertThatThrownBy(() -> sut.give(SESSION, new FeedbackAnswer(9, 7))).isInstanceOfSatisfying(
                ConflictException.class, ex -> assertThat(ex.getCode()).isEqualTo("FEEDBACK_IMPORTED"));
        verify(sessionFeedbackStore, never()).save(any());
    }

    private void at(Instant now) {

        WrapUpWindow wrapUpWindow = () -> Duration.ofDays(30);
        sut = new SessionFeedbackService(sessionStore, sessionFeedbackStore, feedbackImportStore, communityAccess,
                currentUser, organizationContext, wrapUpWindow, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void held(SessionRecord session) {

        when(sessionStore.findByOrganizationIdAndId(ORG, SESSION)).thenReturn(Optional.of(session));
    }

    private void imported() {

        when(feedbackImportStore.find(ORG, SESSION)).thenReturn(Optional.of(FeedbackImportRecord.builder()
                .organizationId(ORG).sessionId(SESSION).scale(5).source("Microsoft Forms").build()));
    }

    private static SessionRecord heldSession() {

        return SessionRecordMother.any().id(SESSION).communityId(COMMUNITY).status("HELD").scheduledAt(SCHEDULED)
                .closedAt(CLOSED_AT).sections(List.of(SessionRecordMother.section(1L))).build();
    }

    private static SessionRecord presentedBy(String userId) {

        var slot = SessionRecordMother.slot().toBuilder()
                .presenters(Set.of(SlotPresenter.builder().userId(userId).build())).build();
        return heldSession().toBuilder()
                .sections(List.of(SectionRecord.builder().slots(List.of(SessionRecordMother.slot(), slot)).build()))
                .build();
    }

    private static FeedbackRecord answered(int enjoyed, int learned) {

        return FeedbackRecord.builder().id(5L).organizationId(ORG).sessionId(SESSION).userId(ALICE).enjoyed(enjoyed)
                .learned(learned).version(2L).build();
    }
}
