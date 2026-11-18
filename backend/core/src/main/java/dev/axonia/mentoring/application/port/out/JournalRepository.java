package dev.axonia.mentoring.application.port.out;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import dev.axonia.mentoring.domain.Note;
import dev.axonia.mentoring.domain.NoteTally;
import dev.axonia.mentoring.domain.Pointer;
import dev.axonia.mentoring.domain.Topic;

public interface JournalRepository {

    long insertTopic(String org, Topic topic);

    void updateTopic(String org, Topic topic);

    void deleteTopic(String org, long pairingId, long topicId);

    /** Writes the state and position of each topic given; the board's slots are checked at commit. */
    void placeTopics(String org, long pairingId, List<Topic> changed);

    List<Topic> topicsOf(String org, long pairingId);

    /** Each pairing's topics in trail order, read at once; a pairing with no topic is absent. */
    Map<Long, List<Topic>> trailsOf(String org, Collection<Long> pairingIds);

    /** Each pairing's notes counted, read at once; a pairing with no note is absent. */
    Map<Long, NoteTally> tallyOf(String org, Collection<Long> pairingIds);

    long insertNote(String org, Note note);

    void updateNote(String org, Note note);

    void deleteNote(String org, long pairingId, long noteId);

    /** By meeting day, newest first, then by when they were written, newest first. */
    List<Note> notesOf(String org, long pairingId);

    /** Whether any note of the pairing names that meeting day, however many notes it holds. */
    boolean hasNoteOn(String org, long pairingId, LocalDate day);

    /** Each note's topics, by note id; a note naming none is absent. */
    Map<Long, List<Long>> noteTopicsOf(String org, long pairingId);

    /** Replaces the topics a note names; a topic of another pairing is never linked. */
    void setNoteTopics(String org, long pairingId, long noteId, List<Long> topicIds);

    long insertPointer(String org, Pointer pointer);

    void deletePointer(String org, long pairingId, long pointerId);

    List<Pointer> pointersOf(String org, long pairingId);

    void renameArea(String org, long communityId, String from, String to);

    void unlinkArea(String org, long communityId, String area);
}
