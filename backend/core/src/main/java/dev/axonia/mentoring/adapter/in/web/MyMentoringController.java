package dev.axonia.mentoring.adapter.in.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.mentoring.api.Journal;
import dev.axonia.mentoring.api.MeetingRequest;
import dev.axonia.mentoring.api.MentoringPresence;
import dev.axonia.mentoring.api.MyMentoring;
import dev.axonia.mentoring.api.NoteRequest;
import dev.axonia.mentoring.api.PairingSummary;
import dev.axonia.mentoring.api.PlannedMeetingView;
import dev.axonia.mentoring.api.PointerRequest;
import dev.axonia.mentoring.api.TopicPlacement;
import dev.axonia.mentoring.api.TopicRequest;
import dev.axonia.mentoring.api.WordsRequest;
import dev.axonia.mentoring.api.WordsView;
import dev.axonia.mentoring.application.JournalService;
import dev.axonia.mentoring.application.MeetingPlanService;
import dev.axonia.mentoring.application.PairingService;
import dev.axonia.mentoring.application.ProposalService;
import dev.axonia.mentoring.application.ShowingService;
import dev.axonia.mentoring.application.WordsService;
import lombok.RequiredArgsConstructor;

/** A member's own mentoring, across every guild, and the journal of each of their pairings. */
@RestController
@RequestMapping("/${spring.application.name}/mentoring")
@RequiredArgsConstructor
class MyMentoringController {

    private final ProposalService proposalService;
    private final PairingService pairingService;
    private final JournalService journalService;
    private final WordsService wordsService;
    private final MeetingPlanService meetingPlanService;
    private final ShowingService showingService;

    @GetMapping("/me")
    MyMentoring mine() {

        return pairingService.mine();
    }

    @GetMapping("/me/presence")
    MentoringPresence presence() {

        return pairingService.presence();
    }

    @PostMapping("/pairings/{id}/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void end(@PathVariable long id) {

        pairingService.end(id);
    }

    @PostMapping("/proposals/{id}/accept")
    PairingSummary accept(@PathVariable long id) {

        return proposalService.accept(id);
    }

    @PostMapping("/proposals/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void decline(@PathVariable long id) {

        proposalService.decline(id);
    }

    @PostMapping("/proposals/{id}/reopen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reopen(@PathVariable long id) {

        proposalService.reopen(id);
    }

    @PostMapping("/proposals/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void withdraw(@PathVariable long id) {

        proposalService.withdraw(id);
    }

    @GetMapping("/pairings/{id}")
    Journal journal(@PathVariable long id) {

        return journalService.journal(id);
    }

    @PostMapping("/pairings/{id}/topics")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void addTopic(@PathVariable long id, @RequestBody TopicRequest request) {

        journalService.addTopic(id, request);
    }

    @PatchMapping("/pairings/{id}/topics/{topicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateTopic(@PathVariable long id, @PathVariable long topicId, @RequestBody TopicRequest request) {

        journalService.updateTopic(id, topicId, request);
    }

    @PutMapping("/pairings/{id}/topics/{topicId}/placement")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void placeTopic(@PathVariable long id, @PathVariable long topicId, @RequestBody TopicPlacement placement) {

        journalService.placeTopic(id, topicId, placement);
    }

    @DeleteMapping("/pairings/{id}/topics/{topicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeTopic(@PathVariable long id, @PathVariable long topicId) {

        journalService.removeTopic(id, topicId);
    }

    @PostMapping("/pairings/{id}/notes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void addNote(@PathVariable long id, @RequestBody NoteRequest request) {

        journalService.addNote(id, request);
    }

    @PatchMapping("/pairings/{id}/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateNote(@PathVariable long id, @PathVariable long noteId, @RequestBody NoteRequest request) {

        journalService.updateNote(id, noteId, request);
    }

    @DeleteMapping("/pairings/{id}/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeNote(@PathVariable long id, @PathVariable long noteId) {

        journalService.removeNote(id, noteId);
    }

    @PostMapping("/pairings/{id}/pointers")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void addPointer(@PathVariable long id, @RequestBody PointerRequest request) {

        journalService.addPointer(id, request);
    }

    @DeleteMapping("/pairings/{id}/pointers/{pointerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removePointer(@PathVariable long id, @PathVariable long pointerId) {

        journalService.removePointer(id, pointerId);
    }

    @PostMapping("/pairings/{id}/meetings")
    @ResponseStatus(HttpStatus.CREATED)
    PlannedMeetingView planMeeting(@PathVariable long id, @RequestBody MeetingRequest request) {

        return meetingPlanService.plan(id, request);
    }

    @PutMapping("/pairings/{id}/meetings/{meetingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changeMeeting(@PathVariable long id, @PathVariable long meetingId, @RequestBody MeetingRequest request) {

        meetingPlanService.change(id, meetingId, request);
    }

    @DeleteMapping("/pairings/{id}/meetings/{meetingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeMeeting(@PathVariable long id, @PathVariable long meetingId) {

        meetingPlanService.remove(id, meetingId);
    }

    @PutMapping("/pairings/{id}/showing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void answerShowing(@PathVariable long id, @RequestBody ShowingRequest request) {

        showingService.answer(id, request.answer());
    }

    @PutMapping("/pairings/{id}/words")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void writeWords(@PathVariable long id, @RequestBody WordsRequest request) {

        wordsService.write(id, request.body());
    }

    @DeleteMapping("/pairings/{id}/words")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void takeWordsBack(@PathVariable long id) {

        wordsService.takeBack(id);
    }

    @PostMapping("/pairings/{id}/words/open")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void openWords(@PathVariable long id) {

        wordsService.open(id);
    }

    @GetMapping("/me/words")
    List<WordsView> myWords() {

        return wordsService.mine();
    }
}
