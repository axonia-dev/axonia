package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.api.Journal;
import dev.axonia.mentoring.api.NoteRequest;
import dev.axonia.mentoring.api.PointerRequest;
import dev.axonia.mentoring.api.TopicPlacement;
import dev.axonia.mentoring.api.TopicRequest;
import dev.axonia.mentoring.application.PairingParties.Party;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.JournalRepository;
import dev.axonia.mentoring.application.port.out.MemberClocks;
import dev.axonia.mentoring.application.port.out.Vocabulary;
import dev.axonia.mentoring.domain.JournalRules;
import dev.axonia.mentoring.domain.MeetingDays;
import dev.axonia.mentoring.domain.Note;
import dev.axonia.mentoring.domain.Pointer;
import dev.axonia.mentoring.domain.PointerAddress;
import dev.axonia.mentoring.domain.ProposalRules;
import dev.axonia.mentoring.domain.Topic;
import dev.axonia.mentoring.domain.TopicBoard;
import dev.axonia.mentoring.domain.TopicState;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;
import dev.axonia.shared.api.web.PublicUrls;
import lombok.RequiredArgsConstructor;

/**
 * The pairing's shared journal. Only its two people read or write it, and to anybody else it does not exist. Either
 * writes topics and pointers; a note is changed or removed only by its author. An ended pairing is frozen, while a
 * guild switched off or archived keeps its journals writable. Every write holds the pairing's row lock, so the caps are
 * counted one write at a time and an end either waits for the write or is seen by it.
 */
@Service
@RequiredArgsConstructor
public class JournalService {

    private static final String INVALID = "field_invalid";

    private final PairingParties parties;
    private final CommunityAccess communityAccess;
    private final JournalRepository journal;
    private final Vocabulary vocabulary;
    private final JournalReads reads;
    private final MemberClocks memberClocks;
    private final PublicUrls publicUrls;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Journal journal(long pairingId) {

        var party = parties.read(pairingId);
        return reads.read(party.org(), party.me(), party.pairing());
    }

    @Transactional
    public void addTopic(long pairingId, TopicRequest request) {

        var party = parties.write(pairingId);
        lockVocabularyForArea(party, request.area());
        var topics = journal.topicsOf(party.org(), pairingId);
        JournalRules.requireRoom(topics.size(), JournalRules.MAX_TOPICS_PER_PAIRING);
        var position = topics.stream().filter(t -> t.state() == TopicState.TODO).mapToInt(Topic::position).max()
                .orElse(-1) + 1;
        journal.insertTopic(party.org(),
                Topic.builder().pairingId(pairingId).label(ProposalRules.topic(request.label()))
                        .area(areaOf(party, request.area(), null)).state(TopicState.TODO).position(position)
                        .createdAt(now()).build());
    }

    @Transactional
    public void updateTopic(long pairingId, long topicId, TopicRequest request) {

        var party = parties.write(pairingId);
        lockVocabularyForArea(party, request.area());
        var topic = topic(party, topicId);
        journal.updateTopic(party.org(), topic.toBuilder().label(ProposalRules.topic(request.label()))
                .area(areaOf(party, request.area(), topic.area())).build());
    }

    /** Moves a topic within its column or to another one; both columns stay numbered from 0 with no gap. */
    @Transactional
    public void placeTopic(long pairingId, long topicId, TopicPlacement placement) {

        var party = parties.write(pairingId);
        var state = stateOf(placement.state());
        var topics = journal.topicsOf(party.org(), pairingId);
        var topic = topicIn(topics, topicId);
        var position = placement.position() == null ? Integer.MAX_VALUE : placement.position();
        journal.placeTopics(party.org(), pairingId, TopicBoard.place(topics, topic.id(), state, position));
    }

    @Transactional
    public void removeTopic(long pairingId, long topicId) {

        var party = parties.write(pairingId);
        var topics = journal.topicsOf(party.org(), pairingId);
        var topic = topicIn(topics, topicId);
        journal.deleteTopic(party.org(), pairingId, topic.id());
        journal.placeTopics(party.org(), pairingId, TopicBoard.without(topics, topic.id()));
    }

    @Transactional
    public void addNote(long pairingId, NoteRequest request) {

        var party = parties.write(pairingId);
        JournalRules.requireRoom(journal.notesOf(party.org(), pairingId).size(), JournalRules.MAX_NOTES_PER_PAIRING);
        var body = JournalRules.note(request.body());
        var meetingOn = meetingDay(party, request.meetingOn());
        var topicIds = namedTopics(party, request.topicIds());
        var noteId = journal.insertNote(party.org(), Note.builder().pairingId(pairingId).authorUserId(party.me())
                .body(body).meetingOn(meetingOn).writtenAt(now()).build());
        journal.setNoteTopics(party.org(), pairingId, noteId, topicIds);
    }

    /** Only a changed meeting day is checked: the note's own day stays valid when its author's zone moved since. */
    @Transactional
    public void updateNote(long pairingId, long noteId, NoteRequest request) {

        var party = parties.write(pairingId);
        var note = myNote(party, noteId);
        var body = JournalRules.note(request.body());
        var unchanged = request.meetingOn() == null || request.meetingOn().equals(note.meetingOn());
        var meetingOn = unchanged ? note.meetingOn() : meetingDay(party, request.meetingOn());
        var topicIds = namedTopics(party, request.topicIds());
        journal.updateNote(party.org(), note.toBuilder().body(body).meetingOn(meetingOn).editedAt(now()).build());
        journal.setNoteTopics(party.org(), pairingId, note.id(), topicIds);
    }

    @Transactional
    public void removeNote(long pairingId, long noteId) {

        var party = parties.write(pairingId);
        journal.deleteNote(party.org(), pairingId, myNote(party, noteId).id());
    }

    @Transactional
    public void addPointer(long pairingId, PointerRequest request) {

        var party = parties.write(pairingId);
        if ((request.topicId() == null) == (request.noteId() == null)) {
            throw new ValidationException(INVALID, "A pointer hangs off exactly one topic or note");
        }
        if (request.topicId() != null) {
            requireTopicInPairing(party, request.topicId());
        } else {
            note(party, request.noteId());
        }
        var parsed = PointerAddress.parse(request.url(), publicUrls.publicBaseUrl());
        var held = journal.pointersOf(party.org(), pairingId).stream()
                .filter(p -> request.topicId() != null
                        ? request.topicId().equals(p.topicId())
                        : request.noteId().equals(p.noteId()))
                .count();
        JournalRules.requireRoom(Math.toIntExact(held), JournalRules.MAX_POINTERS_PER_ENTRY);
        journal.insertPointer(party.org(),
                Pointer.builder().pairingId(pairingId).topicId(request.topicId()).noteId(request.noteId())
                        .kind(parsed.kind()).targetId(parsed.targetId()).url(parsed.url()).createdAt(now()).build());
    }

    @Transactional
    public void removePointer(long pairingId, long pointerId) {

        var party = parties.write(pairingId);
        var pointer = journal.pointersOf(party.org(), pairingId).stream().filter(p -> p.id() == pointerId).findFirst()
                .orElseThrow(() -> new NotFoundException("No such pointer"));
        journal.deletePointer(party.org(), pairingId, pointer.id());
    }

    private Topic topic(Party party, long topicId) {

        return topicIn(journal.topicsOf(party.org(), party.pairingId()), topicId);
    }

    private static Topic topicIn(List<Topic> topics, long topicId) {

        return topics.stream().filter(t -> t.id() == topicId).findFirst()
                .orElseThrow(() -> new NotFoundException("No such topic"));
    }

    private void requireTopicInPairing(Party party, long topicId) {

        topic(party, topicId);
    }

    private Note note(Party party, long noteId) {

        return journal.notesOf(party.org(), party.pairingId()).stream().filter(n -> n.id() == noteId).findFirst()
                .orElseThrow(() -> new NotFoundException("No such note"));
    }

    /** Somebody else's note reads as missing, so only its author learns it is there to change. */
    private Note myNote(Party party, long noteId) {

        var note = note(party, noteId);
        if (!party.me().equals(note.authorUserId())) {
            throw new NotFoundException("No such note");
        }
        return note;
    }

    /** The meeting day, read in the author's own zone, from the pairing's first day to their today. */
    private LocalDate meetingDay(Party party, LocalDate requested) {

        var zone = memberClocks.zoneOf(party.org(), party.me());
        return MeetingDays.meetingDay(requested, MeetingDays.dayOf(party.pairing().startedAt(), zone),
                MeetingDays.dayOf(clock.instant(), zone));
    }

    /** The topics a note names, each a topic of its pairing. */
    private List<Long> namedTopics(Party party, List<Long> requested) {

        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        var held = journal.topicsOf(party.org(), party.pairingId()).stream().map(Topic::id).collect(Collectors.toSet());
        var named = requested.stream().distinct().toList();
        if (!held.containsAll(named)) {
            throw new ValidationException(INVALID, "A note names topics of its own pairing only");
        }
        return named;
    }

    /** Taken before the topic is read, so a curator's rename or delete cannot land between that read and the write. */
    private void lockVocabularyForArea(Party party, String requested) {

        if (requested != null && !requested.isBlank()) {
            vocabulary.shareLock(party.org(), party.pairing().communityId());
        }
    }

    /**
     * A free topic has no area; a linked one names an area the guild carries, under its canonical name. Called after
     * {@link #lockVocabularyForArea}.
     */
    private String areaOf(Party party, String requested, String current) {

        if (requested == null || requested.isBlank()) {
            return null;
        }
        if (requested.equals(current)) {
            return current;
        }
        var community = communityAccess.communityById(party.pairing().communityId());
        var canonical = vocabulary.areasOf(community.id()).canonicalOf(vocabulary.normalized(requested));
        if (!vocabulary.areasIn(community.slug()).contains(canonical)) {
            throw new ValidationException(INVALID, "The guild carries no such area");
        }
        return canonical;
    }

    private static TopicState stateOf(String requested) {

        return Arrays.stream(TopicState.values()).filter(s -> s.name().equals(requested)).findFirst()
                .orElseThrow(() -> new ValidationException(INVALID, "A topic is TODO, IN_PROGRESS or DONE"));
    }

    private Instant now() {

        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
