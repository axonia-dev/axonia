package dev.axonia.mentoring.adapter.in.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.mentoring.api.Journal;
import dev.axonia.mentoring.api.MentoringPresence;
import dev.axonia.mentoring.api.MyMentoring;
import dev.axonia.mentoring.api.NextMeetingDay;
import dev.axonia.mentoring.api.NextMeetingView;
import dev.axonia.mentoring.api.NoteRequest;
import dev.axonia.mentoring.api.NoteView;
import dev.axonia.mentoring.api.PairingSummary;
import dev.axonia.mentoring.api.PointerRequest;
import dev.axonia.mentoring.api.PointerView;
import dev.axonia.mentoring.api.ProposalView;
import dev.axonia.mentoring.api.TopicPlacement;
import dev.axonia.mentoring.api.TopicRequest;
import dev.axonia.mentoring.api.TopicView;
import dev.axonia.mentoring.api.WordsView;
import dev.axonia.mentoring.application.JournalService;
import dev.axonia.mentoring.application.NextMeetingService;
import dev.axonia.mentoring.application.PairingService;
import dev.axonia.mentoring.application.ProposalService;
import dev.axonia.mentoring.application.WordsService;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = MyMentoringController.class)
class MyMentoringControllerTest extends ControllerTestSupport {

    @MockitoBean
    private ProposalService proposalService;
    @MockitoBean
    private PairingService pairingService;
    @MockitoBean
    private JournalService journalService;
    @MockitoBean
    private WordsService wordsService;
    @MockitoBean
    private NextMeetingService nextMeetingService;

    @Test
    void acceptingAnswersThePairingTheFrontendParses() {

        // given
        when(proposalService.accept(40L))
                .thenReturn(PairingSummary.builder().id(90L).communitySlug("platform").communityName("Platform")
                        .myRole("MENTOR").partnerId("u2").partnerName("Noah Keller").partnerGivenName("Noah")
                        .startedAt(Instant.parse("2026-11-16T10:00:00Z")).stops(List.of("TODO")).build());

        // when
        var actual = mvc.post().uri("/axonia/mentoring/proposals/{id}/accept", 40).exchange();

        // then
        actual.assertThat().hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {"id": 90, "communitySlug": "platform", "communityName": "Platform", "myRole": "MENTOR",
                 "partnerId": "u2", "partnerName": "Noah Keller", "partnerGivenName": "Noah", "partnerAvatarUrl": null,
                 "startedAt": "2026-11-16T10:00:00Z", "endedAt": null, "stops": ["TODO"], "nowTopic": null,
                 "nextMeeting": null, "meetings": 0, "notes": 0, "lastMetOn": null}
                """);
    }

    @Test
    void decliningAndWithdrawingAnswerNoContent() {

        // when
        var declined = mvc.post().uri("/axonia/mentoring/proposals/{id}/decline", 40).exchange();
        var withdrawn = mvc.post().uri("/axonia/mentoring/proposals/{id}/withdraw", 41).exchange();

        // then
        declined.assertThat().hasStatus(204);
        withdrawn.assertThat().hasStatus(204);
        verify(proposalService).decline(40L);
        verify(proposalService).withdraw(41L);
    }

    @Test
    void reopeningADeclineAnswersNoContent() {

        // when
        var actual = mvc.post().uri("/axonia/mentoring/proposals/{id}/reopen", 40).exchange();

        // then
        actual.assertThat().hasStatus(204);
        verify(proposalService).reopen(40L);
    }

    @Test
    void reopeningOutOfItsWindowIsAConflict() {

        // given
        doThrow(new ConflictException("MENTORING_PROPOSAL_CLOSED", "Closed")).when(proposalService).reopen(40L);

        // when
        var actual = mvc.post().uri("/axonia/mentoring/proposals/{id}/reopen", 40).exchange();

        // then
        actual.assertThat().hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("MENTORING_PROPOSAL_CLOSED");
    }

    @Test
    void aStrangersProposalIsNotFound() {

        // given
        doThrow(new NotFoundException("No such proposal")).when(proposalService).decline(40L);

        // when
        var actual = mvc.post().uri("/axonia/mentoring/proposals/{id}/decline", 40).exchange();

        // then
        actual.assertThat().hasStatus(404);
    }

    @Test
    void aMentorAtTheLimitIsAConflictWithItsCode() {

        // given
        when(proposalService.accept(40L)).thenThrow(new ConflictException("MENTORING_MENTOR_FULL", "Full"));

        // when
        var actual = mvc.post().uri("/axonia/mentoring/proposals/{id}/accept", 40).exchange();

        // then
        actual.assertThat().hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("MENTORING_MENTOR_FULL");
    }

    @Test
    void myMentoringAnswersTheShapeTheFrontendParses() {

        // given
        var ongoing = PairingSummary.builder().id(90L).communitySlug("platform").communityName("Platform")
                .myRole("MENTEE").partnerId(null).partnerName("Deleted member")
                .startedAt(Instant.parse("2026-11-16T10:00:00Z")).stops(List.of("DONE", "IN_PROGRESS", "TODO"))
                .nowTopic("Kafka").nextMeeting(new NextMeetingDay(LocalDate.parse("2026-11-19"), false)).meetings(2)
                .notes(3).lastMetOn(LocalDate.parse("2026-11-09")).build();
        var received = ProposalView.builder().id(40L).communitySlug("platform").communityName("Platform")
                .proposedBy("MENTEE").partnerId("u1").partnerName("Lea").topics(List.of("Kafka")).message("Hello")
                .status("OPEN").createdAt(Instant.parse("2026-11-16T10:00:00Z")).lapsesOn(LocalDate.parse("2026-12-16"))
                .build();
        when(pairingService.mine()).thenReturn(MyMentoring.builder().today(LocalDate.parse("2026-11-17"))
                .inAMentoringGuild(true).received(List.of(received)).sent(List.of()).ongoing(List.of(ongoing))
                .ended(List.of()).build());

        // when
        var actual = mvc.get().uri("/axonia/mentoring/me").exchange();

        // then
        actual.assertThat().hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {"today": "2026-11-17", "inAMentoringGuild": true,
                 "received": [{"id": 40, "communitySlug": "platform", "communityName": "Platform",
                               "proposedBy": "MENTEE", "partnerId": "u1", "partnerName": "Lea",
                               "partnerAvatarUrl": null, "topics": ["Kafka"], "message": "Hello", "status": "OPEN",
                               "createdAt": "2026-11-16T10:00:00Z", "reopenableUntil": null,
                               "lapsesOn": "2026-12-16"}],
                 "sent": [],
                 "ongoing": [{"id": 90, "communitySlug": "platform", "communityName": "Platform", "myRole": "MENTEE",
                              "partnerId": null, "partnerName": "Deleted member", "partnerGivenName": null,
                              "partnerAvatarUrl": null,
                              "startedAt": "2026-11-16T10:00:00Z", "endedAt": null,
                              "stops": ["DONE", "IN_PROGRESS", "TODO"], "nowTopic": "Kafka",
                              "nextMeeting": {"on": "2026-11-19", "passed": false}, "meetings": 2, "notes": 3,
                              "lastMetOn": "2026-11-09"}],
                 "ended": []}
                """);
    }

    @Test
    void presenceAnswersOneFlagTheShellReads() {

        // given
        when(pairingService.presence()).thenReturn(new MentoringPresence(true));

        // when
        var actual = mvc.get().uri("/axonia/mentoring/me/presence").exchange();

        // then
        actual.assertThat().hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {"inAMentoringGuild": true}
                """);
    }

    @Test
    void endingAPairingAnswersNoContentAndAStrangerGetsNotFound() {

        // given
        doThrow(new NotFoundException("No such pairing")).when(pairingService).end(91L);

        // when
        var ended = mvc.post().uri("/axonia/mentoring/pairings/{id}/end", 90).exchange();
        var stranger = mvc.post().uri("/axonia/mentoring/pairings/{id}/end", 91).exchange();

        // then
        ended.assertThat().hasStatus(204);
        verify(pairingService).end(90L);
        stranger.assertThat().hasStatus(404);
    }

    @Test
    void theJournalAnswersTheShapeTheFrontendParses() {

        // given
        var pairing = PairingSummary.builder().id(90L).communitySlug("platform").communityName("Platform")
                .myRole("MENTEE").partnerId("u2").partnerName("Léa").startedAt(Instant.parse("2026-11-11T10:00:00Z"))
                .stops(List.of("IN_PROGRESS")).build();
        var topic = new TopicView(1L, "Kafka", "kafka", "IN_PROGRESS", 2, List.of(
                new PointerView(5L, "ARTIFACT", "Schema registry notes", "/communities/data/library?focus=70", true),
                new PointerView(6L, "SESSION", null, null, false)));
        var note = NoteView.builder().id(2L).authorId("u2").authorName("Léa").mine(false).body("Covered unit tests")
                .meetingOn(LocalDate.parse("2026-11-11")).topicIds(List.of(1L))
                .writtenAt(Instant.parse("2026-11-11T18:00:00Z"))
                .pointers(List.of(new PointerView(7L, "LINK", null, "https://example.com", true))).build();
        when(journalService.journal(90L)).thenReturn(Journal.builder().pairing(pairing)
                .today(LocalDate.parse("2026-11-16")).startedOn(LocalDate.parse("2026-11-11"))
                .nextMeeting(new NextMeetingView(LocalDate.parse("2026-11-19"), "u2", "Léa", false)).writable(true)
                .topics(List.of(topic)).notes(List.of(note)).wordsWritten(true).canWriteWords(true).myWords(null)
                .build());

        // when
        var actual = mvc.get().uri("/axonia/mentoring/pairings/{id}", 90).exchange();

        // then
        actual.assertThat().hasStatusOk().bodyJson().isStrictlyEqualTo(
                """
                        {"pairing": {"id": 90, "communitySlug": "platform", "communityName": "Platform", "myRole": "MENTEE",
                                     "partnerId": "u2", "partnerName": "Léa", "partnerGivenName": null,
                                     "partnerAvatarUrl": null, "startedAt": "2026-11-11T10:00:00Z", "endedAt": null, "stops": ["IN_PROGRESS"],
                                     "nowTopic": null, "nextMeeting": null, "meetings": 0, "notes": 0, "lastMetOn": null},
                         "today": "2026-11-16", "startedOn": "2026-11-11", "writable": true,
                         "topics": [{"id": 1, "label": "Kafka", "area": "kafka", "state": "IN_PROGRESS",
                                     "position": 2, "pointers": [
                             {"id": 5, "kind": "ARTIFACT", "title": "Schema registry notes",
                              "href": "/communities/data/library?focus=70", "available": true},
                             {"id": 6, "kind": "SESSION", "title": null, "href": null, "available": false}]}],
                         "notes": [{"id": 2, "authorId": "u2", "authorName": "Léa", "authorAvatarUrl": null, "mine": false,
                                    "body": "Covered unit tests", "meetingOn": "2026-11-11", "topicIds": [1],
                                    "writtenAt": "2026-11-11T18:00:00Z", "editedAt": null,
                                    "pointers": [{"id": 7, "kind": "LINK", "title": null, "href": "https://example.com",
                                                  "available": true}]}],
                         "nextMeeting": {"on": "2026-11-19", "setById": "u2", "setByName": "Léa", "passed": false},
                         "wordsWritten": true, "canWriteWords": true, "myWords": null}
                        """);
    }

    @Test
    void journalWritesReadTheirBodiesAndAnswerNoContent() {

        // when
        var addTopic = mvc.post().uri("/axonia/mentoring/pairings/{id}/topics", 90).contentType("application/json")
                .content("""
                        {"label": "Kafka", "area": null}
                        """).exchange();
        var updateTopic = mvc.patch().uri("/axonia/mentoring/pairings/{id}/topics/{topicId}", 90, 1)
                .contentType("application/json").content("""
                        {"label": "Kafka", "area": "kafka"}
                        """).exchange();
        var removeTopic = mvc.delete().uri("/axonia/mentoring/pairings/{id}/topics/{topicId}", 90, 1).exchange();
        var addNote = mvc.post().uri("/axonia/mentoring/pairings/{id}/notes", 90).contentType("application/json")
                .content("""
                        {"body": "Met", "meetingOn": "2026-11-12", "topicIds": [1, 4]}
                        """).exchange();
        var updateNote = mvc.patch().uri("/axonia/mentoring/pairings/{id}/notes/{noteId}", 90, 2)
                .contentType("application/json").content("""
                        {"body": "Met again", "meetingOn": null, "topicIds": null}
                        """).exchange();
        var removeNote = mvc.delete().uri("/axonia/mentoring/pairings/{id}/notes/{noteId}", 90, 2).exchange();
        var addPointer = mvc.post().uri("/axonia/mentoring/pairings/{id}/pointers", 90).contentType("application/json")
                .content("""
                        {"topicId": 1, "noteId": null, "url": "https://example.com"}
                        """).exchange();
        var removePointer = mvc.delete().uri("/axonia/mentoring/pairings/{id}/pointers/{pointerId}", 90, 3).exchange();

        // then
        List.of(addTopic, updateTopic, removeTopic, addNote, updateNote, removeNote, addPointer, removePointer)
                .forEach(result -> result.assertThat().hasStatus(204));
        verify(journalService).addTopic(90L, new TopicRequest("Kafka", null));
        verify(journalService).updateTopic(90L, 1L, new TopicRequest("Kafka", "kafka"));
        verify(journalService).removeTopic(90L, 1L);
        verify(journalService).addNote(90L, new NoteRequest("Met", LocalDate.parse("2026-11-12"), List.of(1L, 4L)));
        verify(journalService).updateNote(90L, 2L, new NoteRequest("Met again", null, null));
        verify(journalService).removeNote(90L, 2L);
        verify(journalService).addPointer(90L, new PointerRequest(1L, null, "https://example.com"));
        verify(journalService).removePointer(90L, 3L);
    }

    @Test
    void nextMeetingRoutes() {

        // when
        var set = mvc.put().uri("/axonia/mentoring/pairings/{id}/next-meeting", 90).contentType("application/json")
                .content("""
                        {"on": "2026-11-19"}
                        """).exchange();
        var cleared = mvc.delete().uri("/axonia/mentoring/pairings/{id}/next-meeting", 90).exchange();

        // then
        set.assertThat().hasStatus(204);
        cleared.assertThat().hasStatus(204);
        verify(nextMeetingService).set(90L, LocalDate.parse("2026-11-19"));
        verify(nextMeetingService).clear(90L);
    }

    @Test
    void aDateBeforeTodayIsABadRequestWithItsCode() {

        // given
        doThrow(new ValidationException("MENTORING_MEETING_PAST", "The next meeting is today or later"))
                .when(nextMeetingService).set(90L, LocalDate.parse("2026-11-01"));

        // when
        var actual = mvc.put().uri("/axonia/mentoring/pairings/{id}/next-meeting", 90).contentType("application/json")
                .content("""
                        {"on": "2026-11-01"}
                        """).exchange();

        // then
        actual.assertThat().hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("MENTORING_MEETING_PAST");
    }

    @Test
    void placementRoute() {

        // when
        var actual = mvc.put().uri("/axonia/mentoring/pairings/{id}/topics/{topicId}/placement", 90, 1)
                .contentType("application/json").content("""
                        {"state": "DONE", "position": 0}
                        """).exchange();

        // then
        actual.assertThat().hasStatus(204);
        verify(journalService).placeTopic(90L, 1L, new TopicPlacement("DONE", 0));
    }

    @Test
    void aStrangersJournalIsNotFoundAndAnEndedOneRefusesWrites() {

        // given
        when(journalService.journal(91L)).thenThrow(new NotFoundException("No such pairing"));
        doThrow(new ConflictException("MENTORING_PAIRING_ENDED", "Ended")).when(journalService).removeNote(90L, 2L);

        // when
        var stranger = mvc.get().uri("/axonia/mentoring/pairings/{id}", 91).exchange();
        var ended = mvc.delete().uri("/axonia/mentoring/pairings/{id}/notes/{noteId}", 90, 2).exchange();

        // then
        stranger.assertThat().hasStatus(404);
        ended.assertThat().hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("MENTORING_PAIRING_ENDED");
    }

    @Test
    void theMenteeWritesAndTakesBackWordsAndTheMentorOpensThem() {

        // when
        var written = mvc.put().uri("/axonia/mentoring/pairings/{id}/words", 90).contentType("application/json")
                .content("{\"body\": \"Thank you\"}").exchange();
        var takenBack = mvc.delete().uri("/axonia/mentoring/pairings/{id}/words", 90).exchange();
        var opened = mvc.post().uri("/axonia/mentoring/pairings/{id}/words/open", 90).exchange();

        // then
        written.assertThat().hasStatus(204);
        takenBack.assertThat().hasStatus(204);
        opened.assertThat().hasStatus(204);
        verify(wordsService).write(90L, "Thank you");
        verify(wordsService).takeBack(90L);
        verify(wordsService).open(90L);
    }

    @Test
    void askingForWordsIsGone() {

        // when
        var actual = mvc.post().uri("/axonia/mentoring/pairings/{id}/words/ask", 90).exchange();

        // then
        assertThat(actual.getResponse().getStatus()).isIn(404, 405);
    }

    @Test
    void myWordsAnswerTheShapeTheFrontendParses() {

        // given
        when(wordsService.mine()).thenReturn(
                List.of(WordsView.builder().pairingId(90L).communitySlug("platform").communityName("Platform")
                        .menteeId(null).menteeName("Deleted member").startedAt(Instant.parse("2026-10-01T10:00:00Z"))
                        .body("Thank you").writtenAt(Instant.parse("2026-11-16T10:00:00Z"))
                        .openedAt(Instant.parse("2026-11-16T11:00:00Z")).build()));

        // when
        var actual = mvc.get().uri("/axonia/mentoring/me/words").exchange();

        // then
        actual.assertThat().hasStatusOk().bodyJson().isStrictlyEqualTo("""
                [{"pairingId": 90, "communitySlug": "platform", "communityName": "Platform", "menteeId": null,
                  "menteeName": "Deleted member", "menteeAvatarUrl": null, "startedAt": "2026-10-01T10:00:00Z",
                  "endedAt": null, "body": "Thank you", "writtenAt": "2026-11-16T10:00:00Z",
                  "openedAt": "2026-11-16T11:00:00Z"}]
                """);
    }
}
